package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.messager.MessageComponent;
import fr.pickaria.messager.MessageType;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.component.RunCommand;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.online.PingOnlineChecker;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public class ConnectionListener {
    private final ProxyServer proxy;
    private final Logger logger;
    private final ConfigurationLoader configurationLoader;
    private final Map<String, StartingServer> startingServers = new HashMap<>();
    private final ShutdownManager shutdownManager;
    private final Messager messager;

    ConnectionListener(
            ConfigurationLoader configurationLoader,
            ProxyServer proxy,
            Logger logger,
            ShutdownManager shutdownManager
    ) {
        this.configurationLoader = configurationLoader;
        this.proxy = proxy;
        this.logger = logger;
        this.shutdownManager = shutdownManager;
        this.messager = new Messager();
    }

    @Subscribe()
    public void onServerConnected(ServerConnectedEvent event) {
        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        // Check if we can shut down the previous server once the player has been redirected
        // This applies to redirection if the server is already running
        // and the automatic redirection after a server has been started
        previousServer.ifPresent(shutdownManager::scheduleShutdown);
    }

    @Subscribe()
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer originalServer = event.getOriginalServer();
      
        // If the server is not managed, simply ignore it
        if (!this.isManagedServer(originalServer)) {
            return;
        }

        String serverName = originalServer.getServerInfo().getName();
        if (configurationLoader.getConfiguration().shouldCheckWhitelist(serverName)) {
            try {
                boolean whitelisted = configurationLoader.getAPI()
                        .isPlayerWhitelisted(serverName, event.getPlayer().getUsername())
                        .join();
                if (!whitelisted) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    event.getPlayer().disconnect(Component.translatable("whitelist.not.whitelisted"));
                    return;
                }
            } catch (Exception e) {
                logger.error("Failed to check whitelist for server {}", serverName, e);
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().disconnect(Component.translatable("whitelist.verification.failed"));
                return;
            }
        }

        RegisteredServer previousServer = event.getPreviousServer();
        shutdownManager.cancelTask(originalServer);

        if (isReachable(originalServer)) {
            // Server pinged successfully, we can connect the player to this server
            event.setResult(ServerPreConnectEvent.ServerResult.allowed(originalServer));
        } else {
            boolean isAlreadyConnected = previousServer != null;
            if (isAlreadyConnected) {
                // If the player is already connected on the network, we don't want to redirect it to the waiting server
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            } else {
                Optional<RegisteredServer> waitingServer = getWaitingServer();

                if (waitingServer.isPresent() && waitingServer.get() != originalServer && isReachable(waitingServer.get())) {
                    // Server is not running, inform the player and redirect somewhere else
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(waitingServer.get()));
                } else {
                    // If the waiting server is not reachable, we kick the player instead
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    event.getPlayer().disconnect(Component.translatable("kick.server.starting", Component.text(originalServer.getServerInfo().getName())));
                }
            }

            startServerForPlayer(originalServer, event.getPlayer());
        }
    }

    private void startServerForPlayer(RegisteredServer server, Player player) {
        String originalServerName = server.getServerInfo().getName();
        boolean playerAddedToWaitingList;

        // This is cached so that we don't ping the same server for every player that is waiting for it to start
        if (startingServers.containsKey(originalServerName)) {
            playerAddedToWaitingList = startingServers.get(originalServerName).addPlayer(player);
        } else {
            StartingServer startingServer = new StartingServer(server, configurationLoader, shutdownManager, logger, messager);
            playerAddedToWaitingList = startingServer.addPlayer(player);
            startingServers.put(originalServerName, startingServer);
            // TODO: Should we clear the entry from the map once the server is started?
        }

        if (playerAddedToWaitingList) {
            Component message = messager.format(MessageType.INFO, "starting.server", new Text(Component.text(originalServerName)));
            player.sendMessage(message);
        }
    }

    @Subscribe()
    public void onDisconnect(DisconnectEvent event) {
        scheduleServerShutdown(event.getPlayer());
    }

    @Subscribe()
    public void onKicked(KickedFromServerEvent event) {
        if (isManagedServer(event.getServer())) {
            scheduleServerShutdown(event.getPlayer());
            redirectPlayerToWaitingServerOnKick(event);
        }
    }

    private void redirectPlayerToWaitingServerOnKick(KickedFromServerEvent event) {
        Optional<RegisteredServer> waitingServerOpt = getWaitingServer();

        // If the waiting server is not available or redirection is disabled, disconnect the player
        if (waitingServerOpt.isEmpty() || !configurationLoader.getConfiguration().getRedirectToWaitingServerOnKick()) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(getKickDisconnectMessage(event)));
            return;
        }

        RegisteredServer waitingServer = waitingServerOpt.get();

        // If the player was kicked from the waiting server itself, disconnect them
        if (event.getServer() == waitingServer) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(getKickDisconnectMessage(event)));
            return;
        }

        // Check if the player is already connected to the waiting server
        boolean isConnectedToWaitingServer = event.getPlayer().getCurrentServer()
                .map(serverConnection -> serverConnection.getServer() == waitingServer)
                .orElse(false);

        if (isConnectedToWaitingServer) {
            // If already on the waiting server, notify with the kick message
            event.setResult(KickedFromServerEvent.Notify.create(getKickDisconnectMessage(event)));
        } else if (isReachable(waitingServer)) {
            // Otherwise redirect to the waiting server
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(waitingServer, getKickRedirectMessage(event)));
        }

        scheduleServerShutdown(event.getServer());
    }

    private Component getKickDisconnectMessage(KickedFromServerEvent event) {
        return event.getServerKickReason().orElse(Component.translatable("kick.generic.disconnect", Component.text(event.getServer().getServerInfo().getName())));
    }

    private Component getKickRedirectMessage(KickedFromServerEvent event) {
        Optional<Component> serverKickReason = event.getServerKickReason();
        String serverName = event.getServer().getServerInfo().getName();
        String serverCommand = "/server " + serverName;
        Component serverNameComponent = Component.text(serverName);
        MessageComponent goBack = new RunCommand(serverCommand, Component.translatable("go.back.command", serverNameComponent));
        return serverKickReason.map(component -> messager.format(MessageType.INFO, "kick.reason.message", new Text(serverNameComponent), new Text(component), goBack))
                .orElseGet(() -> messager.format(MessageType.INFO, "kick.generic.message", new Text(serverNameComponent), goBack));
    }

    private void scheduleServerShutdown(Player player) {
        Optional<ServerConnection> serverConnection = player.getCurrentServer();
        if (serverConnection.isPresent()) {
            RegisteredServer currentServer = serverConnection.get().getServer();
            scheduleServerShutdown(currentServer);
        }
    }

    private void scheduleServerShutdown(RegisteredServer registeredServer) {
        if (this.isManagedServer(registeredServer)) {
            shutdownManager.scheduleShutdown(registeredServer);
        }
    }

    private boolean isManagedServer(RegisteredServer server) {
        String serverName = server.getServerInfo().getName();
        return configurationLoader.getConfiguration().getAllServers().contains(serverName);
    }

    private Optional<RegisteredServer> getWaitingServer() {
        return configurationLoader.getConfiguration().getWaitingServerName().flatMap(proxy::getServer);
    }

    private boolean isReachable(RegisteredServer server) {
        try {
            // FIXME: This may be blocking the main thread
            return configurationLoader.getOnlineChecker(server).isRunningNow();
        } catch (NoSuchElementException exception) {
            logger.error("Server '{}' does not have its Pterodactyl ID configured in the plugin's configuration", server.getServerInfo().getName(), exception);
            return false;
        } catch (IllegalArgumentException exception) {
            logger.error("The Pterodactyl URL is missing or invalid in the plugin's configuration", exception);
            return false;
        }
    }
}
