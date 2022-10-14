package org.mcmodule.tunnel;

import java.nio.channels.SelectableChannel;
import java.util.LinkedList;

import org.bukkit.entity.Player;

public class ConnectionStatus {

	protected final Player player;
	protected final SelectableChannel channel;
	protected final long id;
	protected final LinkedList<byte[]> outgoingData = new LinkedList<>();
	protected boolean readable, writable;
	
	public ConnectionStatus(Player player, SelectableChannel channel, long id) {
		this.player = player;
		this.channel = channel;
		this.id = id;
		this.readable = this.writable = true;
	}
}
