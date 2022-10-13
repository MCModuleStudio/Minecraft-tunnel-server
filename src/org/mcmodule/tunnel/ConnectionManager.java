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
		loop:
		while(true) {
			try {
				selector.selectedKeys().clear();
				selector.selectNow();
				for(SelectionKey key : selector.selectedKeys()) {
					ConnectionStatus status = (ConnectionStatus) key.attachment();
					if(!key.isValid() || !(status.readable || status.writable)) {
						closeConnection(status);
						continue;
					}
					if(key.isReadable() && status.readable) {
						buffer.clear();
						int length = ((ReadableByteChannel) key.channel()).read(buffer);
						if(length == 0 && status.readable) {
							tunnel.sendShutdownInput(status);
							status.readable = false;
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
								if(channel.write(buffer) == 0) { // Not writable???
									tunnel.sendShutdownOutput(status);
									status.writable = false;
									break;
								}
							} while(buffer.hasRemaining());
						}
					} else if(status.writable) {
						tunnel.sendShutdownOutput(status);
						status.writable = false;
					}
				}
				Thread.sleep(1L);
			} catch (InterruptedException e) {
				break loop;
			} catch(Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private void closeConnection(ConnectionStatus connectionStatus) {
		try {
			connectionStatus.channel.close();
		} catch(Throwable t) {
			t.printStackTrace();
		}
		connections.remove(connectionStatus.id);
		tunnel.sendCloseConnection(connectionStatus);
	}

	public ConnectionStatus getConnectionStatus(long id) {
		return connections.get(Long.valueOf(id));
	}

	public void writeData(ConnectionStatus connectionStatus, ByteBuf buf) {
		if(!connectionStatus.writable) return; // 不可写立即丢弃所有数据
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
			channel.keyFor(selector);
			long id = allocateNewConnectionID();
			ConnectionStatus status = new ConnectionStatus(player, channel, id);
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
