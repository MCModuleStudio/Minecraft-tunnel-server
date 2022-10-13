package org.mcmodule.tunnel;

import java.nio.channels.Channel;
import java.util.LinkedList;

import org.bukkit.entity.Player;

public class ConnectionStatus {

	protected final Player player;
	protected final Channel channel;
	protected final long id;
	protected final LinkedList<byte[]> outgoingData = new LinkedList<>();
	protected boolean readable, writable;
	
	public ConnectionStatus(Player player, Channel channel, long id) {
		this.player = player;
		this.channel = channel;
		this.id = id;
		this.readable = this.writable = true;
	}
}
