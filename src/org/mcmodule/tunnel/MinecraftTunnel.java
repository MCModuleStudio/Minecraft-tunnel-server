package org.mcmodule.tunnel;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.bukkit.entity.Player;
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
	private ConnectionManager manager;
	private static final ListeningWhitelist list = ListeningWhitelist.newBuilder().normal().types(PacketType.Play.Client.CUSTOM_PAYLOAD).gamePhase(GamePhase.PLAYING).build();
	
	public void onEnable() {
		protocolManager = ProtocolLibrary.getProtocolManager();
		protocolManager.addPacketListener(this);
		try {
			this.manager = new ConnectionManager(this);
			manager.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void sendConnectionStatus(ConnectionStatus connectionStatus, String status) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.CUSTOM_PAYLOAD);
		packet.getStrings().write(0, "tunnel-" + status);
		ByteBuf buf = (ByteBuf) MinecraftReflection.getPacketDataSerializer(8);
		buf.writeLong(connectionStatus.id);
		packet.getModifier().write(1, buf);
		try {
			protocolManager.sendServerPacket(connectionStatus.player, packet);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
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
		try {
			protocolManager.sendServerPacket(status.player, packet);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	public void sendConnectFailed(Player player, String message, long tmpid) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.CUSTOM_PAYLOAD);
		packet.getStrings().write(0, "tunnel-failed");
		byte[] data = message.getBytes(StandardCharsets.UTF_8);
		int length = data.length;
		ByteBuf buf = (ByteBuf) MinecraftReflection.getPacketDataSerializer(8 + 4 + length);
		buf.writeLong(tmpid);
		buf.writeInt(length);
		buf.writeBytes(data);
		packet.getModifier().write(1, buf);
		try {
			protocolManager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public void sendConnected(ConnectionStatus status, long tmpid) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Client.CUSTOM_PAYLOAD);
		packet.getStrings().write(0, "tunnel-success");
		ByteBuf buf = (ByteBuf) MinecraftReflection.getPacketDataSerializer(8 + 8);
		buf.writeLong(tmpid);
		buf.writeLong(status.id);
		packet.getModifier().write(1, buf);
		try {
			protocolManager.sendServerPacket(status.player, packet);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
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
						String ip = new String(data, 10, data.length - 10, StandardCharsets.ISO_8859_1.name()); // 谁寄吧用中文域名啊
						long tmpid = (data[0] & 0xFF) << 56 | (data[1] & 0xFF) << 48 | (data[2] & 0xFF) << 40 | (data[3] & 0xFF) << 32 |
									 (data[4] & 0xFF) << 24 | (data[5] & 0xFF) << 16 | (data[6] & 0xFF) <<  8 | (data[7] & 0xFF) <<  0;
						int port = (data[8] & 0xFF) << 8 | (data[9] & 0xFF) << 0; // 大端
						manager.connect(event.getPlayer(), tmpid, ip, port);
					}
					case "data": {
						long id = buf.readLong();
						manager.writeData(manager.getConnectionStatus(id), buf);
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

}
