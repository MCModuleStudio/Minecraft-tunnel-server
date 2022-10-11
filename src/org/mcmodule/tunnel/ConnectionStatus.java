package org.mcmodule.tunnel;

import java.util.LinkedList;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;

public class ConnectionStatus {

	protected final Player player;
	protected final Channel channel;
	protected final long id;
	protected final LinkedList<byte[]> outgoingData = new LinkedList<>();
	protected boolean readable, writeable;
	
	public ConnectionStatus(Player player, Channel channel, long id) {
		this.player = player;
		this.channel = channel;
		this.id = id;
		this.readable = this.writeable = true;
	}
}
