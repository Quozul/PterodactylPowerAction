package fr.pickaria.pterodactylpoweraction.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/**
 * A record representing a player waiting in the queue for a specific server.
 * @param player The player in the queue.
 * @param targetServer The server the player originally intended to join.
 */
public record QueueEntry(Player player, RegisteredServer targetServer) {
}