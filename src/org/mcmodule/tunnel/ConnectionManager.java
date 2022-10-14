package org.mcmodule.tunnel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import io.netty.buffer.ByteBuf;

public class ConnectionManager extends Thread {

	private final Selector selector;
	private final MinecraftTunnel tunnel;
	private final ByteBuffer buffer = ByteBuffer.allocate(32767 - 8); // Allocate shared 32k buffer
	private final ConcurrentHashMap<Long, ConnectionStatus> connections = new ConcurrentHashMap<>();
	private final static Random rand = new Random();
	
	public ConnectionManager(MinecraftTunnel tunnel) throws IOException {
		super("Tunnel Thread");
		setPriority(MAX_PRIORITY);
		selector = Selector.open();
		this.tunnel = tunnel;
	}
	
	@Override
	public void run() {
		loop1:
		while(true) {
			try {
				runLoop();
			} catch (InterruptedException e) {
				break loop1;
			} catch(Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private void runLoop() throws Throwable {
		selector.selectedKeys().clear();
		selector.selectNow();
		loop2:
		for(SelectionKey key : selector.selectedKeys()) {
			ConnectionStatus status = (ConnectionStatus) key.attachment();
			if(!(status.readable || status.writable))  {
				closeConnection(status);
			}
			if(!key.isValid()) continue;
			try {
				if(key.isReadable() && status.readable) {
					buffer.clear();
					int length = ((ReadableByteChannel) key.channel()).read(buffer);
					if(length <= 0 && status.readable) {
						closeConnection(status);
						continue loop2;
					} else {
						buffer.flip();
						tunnel.sendData(status, buffer);
					}
				}
				if(key.isWritable() && status.writable) {
					byte[] data = status.outgoingData.poll();
					if(data != null) {
						buffer.clear();
						buffer.put(data).flip();
						WritableByteChannel channel = (WritableByteChannel) key.channel();
						do {
							if(channel.write(buffer) <= 0) { // Not writable???
								closeConnection(status);
								continue loop2;
							}
						} while(buffer.hasRemaining());
					}
				} else if(status.writable) {
					tunnel.sendShutdownOutput(status);
					status.writable = false;
				}
			} catch(IOException e) {
				e.printStackTrace();
				closeConnection(status);
			}
		}
	}

	public void closeConnection(ConnectionStatus connectionStatus) {
		try {
			connectionStatus.channel.close();
		} catch(Throwable t) {
			t.printStackTrace();
		}
		connections.remove(connectionStatus.id);
		tunnel.sendCloseConnection(connectionStatus);
		connectionStatus.channel.keyFor(selector).cancel();
	}

	public ConnectionStatus getConnectionStatus(long id) {
		return connections.get(Long.valueOf(id));
	}

	public void writeData(ConnectionStatus connectionStatus, ByteBuf buf) {
		if(connectionStatus == null || !connectionStatus.writable) return; // 不可写立即丢弃所有数据
		byte[] data = new byte[buf.readableBytes()];
		if(data.length != 0) {
			buf.readBytes(data);
			connectionStatus.outgoingData.add(data);
		}
	}

	public void connect(Player player, long tmpid, String ip, int port) {
		try {
			SocketChannel channel = SocketChannel.open(new InetSocketAddress(ip, port));
			channel.configureBlocking(false);
			channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
			long id = allocateNewConnectionID();
			ConnectionStatus status = new ConnectionStatus(player, channel, id);
			channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, status);
			connections.put(id, status);
			tunnel.sendConnected(status, tmpid);
		} catch (IOException e) {
			e.printStackTrace();
			tunnel.sendConnectFailed(player, e.getMessage(), tmpid);
		}
	}
	
	private long allocateNewConnectionID() {
		long id;
		do {
			id = rand.nextLong();
		} while(connections.containsKey(id));
		return id;
	}

}
