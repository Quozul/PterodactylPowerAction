package dev.quozul.pterodactylpoweractionqueue.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.quozul.pterodactylpoweractionqueue.config.ConfigManager;
import dev.quozul.pterodactylpoweractionqueue.queue.QueueService;
import fr.pickaria.pterodactylpoweractionapi.events.StartServerEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class QueueListener {
    private final ProxyServer server;
    private final QueueService queueService;
    private final ConfigManager config;

    public QueueListener(ProxyServer server, QueueService queueService, ConfigManager config) {
        this.server = server;
        this.queueService = queueService;
        this.config = config;
    }

    @Subscribe(priority = 20)
    public void onStartServer(StartServerEvent event) {
        if (!config.isQueueEnabled()) {
            return;
        }

        Player player = event.player();
        RegisteredServer target = event.targetServer();

        // If player is already in the queue, deny changing server
        if (queueService.isPlayerInQueue(player)) {
            player.sendMessage(Component.text("You are currently in the queue, please wait for your turn.", NamedTextColor.GOLD));
            event.setResult(StartServerEvent.StartServerResult.denied());
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
                player.disconnect(Component.text("The limbo server is offline. Please try again later.", NamedTextColor.RED));
                return;
            }

            // Redirect to limbo and add to queue
            event.setResult(StartServerEvent.StartServerResult.allowed(limboServer.get()));
            queueService.addPlayer(player, target);

            player.sendMessage(Component.text("The server is full. You have been added to the queue.", NamedTextColor.GOLD));
        }
    }

    @Subscribe(priority = 1)
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Remove player from queue if they were in it
        queueService.removePlayer(player);

        // A slot may have opened up, check if we can let someone in.
        // This check runs regardless of which server the player disconnected from.
        tryConnectNextPlayer();
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
                    playerToConnect.sendMessage(Component.text("Your turn! Connecting you to ", NamedTextColor.GREEN)
                            .append(Component.text(targetServer.getServerInfo().getName(), NamedTextColor.AQUA))
                            .append(Component.text("...", NamedTextColor.GREEN)));

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
