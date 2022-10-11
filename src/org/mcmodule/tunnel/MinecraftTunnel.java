package org.mcmodule.tunnel;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.injector.GamePhase;
import com.comphenix.protocol.utility.MinecraftReflection;

import io.netty.buffer.ByteBuf;

public class MinecraftTunnel extends JavaPlugin implements PacketListener  {
	public MinecraftTunnel() {}
	
	private ProtocolManager protocolManager;
	private static final ListeningWhitelist list = ListeningWhitelist.newBuilder().normal().types(PacketType.Play.Client.CUSTOM_PAYLOAD).gamePhase(GamePhase.PLAYING).build();
	
	public void onEnable() {
		protocolManager = ProtocolLibrary.getProtocolManager();
		protocolManager.addPacketListener(this);
		
	}
	
	private void sendConnectionStatus(ConnectionStatus connectionStatus, String status) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.CUSTOM_PAYLOAD);
		packet.getStrings().write(0, "tunnel-" + status);
		ByteBuf buf = (ByteBuf) MinecraftReflection.getPacketDataSerializer(8);
		buf.writeLong(connectionStatus.id);
		packet.getModifier().write(1, buf);
	}
	
	public void sendCloseConnection(ConnectionStatus status) {
		sendConnectionStatus(status, "close");
	}
	
	public void sendShutdownInput(ConnectionStatus status) {
		sendConnectionStatus(status, "shutdowninput");
	}
	
	public void sendShutdownOutput(ConnectionStatus status) {
		sendConnectionStatus(status, "shutdownoutput");
	}
	
	public void sendData(ConnectionStatus status, ByteBuffer buffer) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.CUSTOM_PAYLOAD);
		packet.getStrings().write(0, "tunnel-data");
		ByteBuf buf = (ByteBuf) MinecraftReflection.getPacketDataSerializer(8 + buffer.remaining());
		buf.writeLong(status.id);
		buf.writeBytes(buffer);
		packet.getModifier().write(1, buf);
	}

	@Override
	public Plugin getPlugin() {
		return this;
	}

	@Override
	public ListeningWhitelist getReceivingWhitelist() {
		return list;
	}

	@Override
	public ListeningWhitelist getSendingWhitelist() {
		return ListeningWhitelist.EMPTY_WHITELIST;
	}

	@Override
	public void onPacketReceiving(PacketEvent event) {
		try {
			PacketContainer packet = event.getPacket();
			if(packet.getType() == PacketType.Play.Client.CUSTOM_PAYLOAD) {
				String channel = packet.getStrings().read(0);
				if(channel.startsWith("tunnel-")) {
					String command = channel.substring(7);
					ByteBuf buf = (ByteBuf) packet.getModifier().read(1);
					switch(command) {
					case "connect": {
						byte[] data = getByteBufArray(buf);
						String ip = new String(data, 2, data.length - 2, StandardCharsets.ISO_8859_1.name());
						int port = (data[0] & 0xFF) << 8 | (data[1] & 0xFF) << 0; // 大端
					}
					case "data": {
						long id = buf.readLong();
						
					}
					}
				}
			}
		} catch(Throwable t) {
			throw new RuntimeException(t);
		}
	}

	private static byte[] getByteBufArray(Object bytebuf) {
		return (byte[]) ((ByteBuf) bytebuf).array();
	}

	@Override
	public void onPacketSending(PacketEvent arg0) {}
	
	private static final Method ARRAY_METHOD;
    static {
        try {
            ARRAY_METHOD = Class.forName("io.netty.buffer.UnpooledHeapByteBuf").getMethod("array");
        } catch (NoSuchMethodException | ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

}
