package org.mcmodule.tunnel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class ConnectionManager extends Thread {

	private final Selector selector;
	private final MinecraftTunnel tunnel;
	private final ByteBuffer buffer = ByteBuffer.allocate(32767 - 8); // Allocate shared 32k buffer
	
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
							
						}
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

}
