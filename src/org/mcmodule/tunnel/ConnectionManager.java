package org.mcmodule.tunnel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;

import io.netty.buffer.ByteBuf;

public class ConnectionManager extends Thread {

	private final Selector selector;
	private final MinecraftTunnel tunnel;
	private final ByteBuffer buffer = ByteBuffer.allocate(32767 - 8); // Allocate shared 32k buffer
	private final HashMap<Long, ConnectionStatus> connections = new HashMap<>();
	
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
					if(!key.isValid() || !(status.readable || status.writeable)) {
						closeConnection(status);
						continue;
					}
					if(key.isReadable()) {
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
					if(key.isWritable()) {
						byte[] data = status.outgoingData.poll();
						if(data != null) {
							buffer.clear().put(data).flip();
							WritableByteChannel channel = (WritableByteChannel) key.channel();
							do {
								if(channel.write(buffer) == 0) {
									tunnel.sendShutdownOutput(status);
									status.writeable = false;
									break;
								}
							} while(buffer.hasRemaining());
						}
					} else if(status.writeable) {
						tunnel.sendShutdownOutput(status);
						status.writeable = false;
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
		tunnel.sendCloseConnection(connectionStatus);
	}

	public ConnectionStatus getConnectionStatus(long id) {
		return connections.get(Long.valueOf(id));
	}

	public void writeData(ConnectionStatus connectionStatus, ByteBuf buf) {
		if(!connectionStatus.writeable) return; // 不可写立即丢弃所有数据
		byte[] data = new byte[buf.readableBytes()];
		if(data.length != 0) {
			buf.readBytes(data);
			connectionStatus.outgoingData.add(data);
		}
	}

}
