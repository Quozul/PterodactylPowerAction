package fr.pickaria.pterodactylpoweraction.listeners;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.configuration.QueueConfig;
import fr.pickaria.pterodactylpoweraction.queue.QueueService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class Queue {
    private final ProxyServer server;
    private final QueueService queueService;
    private final QueueConfig config;
    private final Messager messager;

    public Queue(ProxyServer server, QueueService queueService, QueueConfig config, Messager messager) {
        this.server = server;
        this.queueService = queueService;
        this.config = config;
        this.messager = messager;
    }

    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (!config.isQueueEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        RegisteredServer target = event.getOriginalServer();

        // If player is already in the queue, deny changing server
        if (queueService.isPlayerInQueue(player)) {
            messager.info(player, "queue.player.in.queue");
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            return;
        }

        // If the player is already on a server, they have passed the queue. Allow them to switch freely.
        if (player.getCurrentServer().isPresent()) {
            return;
        }

        long currentPlayers = getTotalPlayerCount();
        if (currentPlayers >= config.getMaxPlayers()) {
            Optional<RegisteredServer> limboServer = server.getServer(config.getLimboServerName());

            if (limboServer.isEmpty()) {
                player.disconnect(Component.translatable("queue.limbo.server.offline", NamedTextColor.RED));
                return;
            }

            // Redirect to limbo and add to queue
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(limboServer.get()));
            queueService.addPlayer(player, target);

            messager.info(player, "queue.server.full.added.to.queue");
        }
    }

    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Remove player from queue if they were in it
        queueService.removePlayer(player);

        // A slot may have opened up, check if we can let someone in.
        // This check runs regardless of which server the player disconnected from.
        tryConnectNextPlayer();
    }

    public boolean isInQueue(Player player) {
        return queueService.isPlayerInQueue(player);
    }

    // This should also be called when a player successfully connects to a game server,
    // but DisconnectEvent covers the main use case of a slot opening.
    // For a more robust system, you might hook into a "post-connect" event.
    // For now, Disconnect is a good starting point.
    private void tryConnectNextPlayer() {
        if (getTotalPlayerCount() - 1 < config.getMaxPlayers()) {
            queueService.pollNextPlayer().ifPresent(entry -> {
                Player playerToConnect = entry.player();
                RegisteredServer targetServer = entry.targetServer();

                // Make sure the player is still on the limbo server and online
                if (playerToConnect.getCurrentServer().isPresent() && playerToConnect.getCurrentServer().get().getServerInfo().getName().equals(config.getLimboServerName())) {
                    messager.info(playerToConnect, "queue.connecting.to.server", new Text(Component.text(targetServer.getServerInfo().getName())));
                    playerToConnect.createConnectionRequest(targetServer).fireAndForget();
                }
            });
        }
    }

    private long getTotalPlayerCount() {
        return server.getAllServers().stream()
                .filter(this::isServerCounted)
                .mapToLong(s -> s.getPlayersConnected().size())
                .sum();
    }

    private boolean isServerCounted(RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        return !serverName.equalsIgnoreCase(config.getLimboServerName()) &&
                !config.getIgnoredServers().contains(serverName);
    }
}
