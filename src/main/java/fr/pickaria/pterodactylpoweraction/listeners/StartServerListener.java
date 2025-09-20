package fr.pickaria.pterodactylpoweraction.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.messager.MessageType;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.ShutdownManager;
import fr.pickaria.pterodactylpoweraction.StartingServer;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweractionapi.events.ScheduleShutdownServerEvent;
import fr.pickaria.pterodactylpoweractionapi.events.StartServerEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

public class StartServerListener {
    private final Map<String, StartingServer> startingServers = new HashMap<>();
    private final Messager messager = new Messager();
    private final ConfigurationLoader configurationLoader;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ShutdownManager shutdownManager;

    public StartServerListener(
            ConfigurationLoader configurationLoader,
            ProxyServer proxy,
            Logger logger,
            ShutdownManager shutdownManager
    ) {
        this.configurationLoader = configurationLoader;
        this.proxy = proxy;
        this.logger = logger;
        this.shutdownManager = shutdownManager;
    }

    @Subscribe()
    public void onStartServer(StartServerEvent event) {
        if (event.getResult().isAllowed()) {
            RegisteredServer targetServer = event.targetServer();
            if (isReachable(targetServer)) {
                // Server pinged successfully, we can connect the player to this server
                event.setResult(StartServerEvent.StartServerResult.allowed(targetServer));
            } else {
                // Server is not started
                shutdownManager.cancelTask(targetServer);
                if (event.isAlreadyConnected()) {
                    // If the player is already connected on the network, we don't want to redirect it to the waiting server
                    event.setResult(StartServerEvent.StartServerResult.denied());
                } else {
                    Optional<RegisteredServer> waitingServer = getWaitingServer();

                    if (waitingServer.isPresent() && waitingServer.get() != targetServer && isReachable(waitingServer.get())) {
                        // Server is not running, inform the player and redirect somewhere else
                        event.setResult(StartServerEvent.StartServerResult.allowed(waitingServer.get()));
                    } else {
                        // If the waiting server is not reachable, we kick the player instead
                        event.setResult(StartServerEvent.StartServerResult.denied());
                        event.player().disconnect(Component.translatable("kick.server.starting", Component.text(targetServer.getServerInfo().getName())));
                    }
                }

                startServerForPlayer(targetServer, event.player());
            }
        }
    }

    @Subscribe()
    public void onScheduleShutdownServerEvent(ScheduleShutdownServerEvent event) {
        shutdownManager.scheduleShutdown(event.targetServer());
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
