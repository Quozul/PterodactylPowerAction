package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.configuration.ShutdownBehaviour;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ShutdownManager {
    private static ShutdownManager instance;
    private final ProxyServer proxy;
    private final PterodactylPowerAction plugin;
    private final ConfigurationLoader configurationLoader;
    private final Map<String, ScheduledTask> shutdownTasks = new HashMap<>();
    private final Logger logger;

    private final fr.pickaria.pterodactylpoweraction.state.MotdCache motdCache;

    public ShutdownManager(ProxyServer proxy, PterodactylPowerAction plugin, ConfigurationLoader configurationLoader, Logger logger, fr.pickaria.pterodactylpoweraction.state.MotdCache motdCache) {
        assert instance == null; // Simply to make sure we only instantiate this class once
        instance = this;

        this.proxy = proxy;
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.logger = logger;
        this.motdCache = motdCache;
    }

    /**
     * Start a task that will shut down the server after the configured delay.
     *
     * @param server The server we want to shut down
     */
    public void scheduleShutdown(RegisteredServer server) {
        scheduleShutdown(server, configurationLoader.getConfiguration().getShutdownAfterDuration());
    }

    public void scheduleShutdown(RegisteredServer server, Duration afterDuration) {
        scheduleShutdownTask(server, afterDuration);
    }

    public void shutdownAll(ShutdownBehaviour shutdownBehaviour, Duration afterDuration) {
        switch (shutdownBehaviour) {
            case SHUTDOWN_EMPTY: {
                Configuration configuration = configurationLoader.getConfiguration();

                for (String serverName : configuration.getAllServers()) {
                    proxy.getServer(serverName)
                            .ifPresent(registeredServer -> scheduleShutdown(registeredServer, afterDuration));
                }
            }
            case SHUTDOWN_ALL: {
                Configuration configuration = configurationLoader.getConfiguration();
                PowerActionAPI api = configurationLoader.getAPI();

                for (String serverName : configuration.getAllServers()) {
                    if (!isWaitingServer(serverName)) {
                        api.stop(serverName);
                    }
                }
            }
        }
    }

    /**
     * Cancel any shutdown task for the requested server.
     *
     * @param server The server we don't want to stop anymore
     */
    public void cancelTask(RegisteredServer server) {
        String serverName = getServerName(server);
        if (shutdownTasks.containsKey(serverName)) {
            logger.debug("Cancelling shutdown for server '{}'.", serverName);
            shutdownTasks.get(serverName).cancel();
            shutdownTasks.remove(serverName);
        }
    }

    private boolean isServerEmpty(RegisteredServer server) {
        return server.getPlayersConnected().isEmpty();
    }

    private String getServerName(RegisteredServer server) {
        return server.getServerInfo().getName();
    }

    private void scheduleShutdownTask(RegisteredServer server, Duration delay) {
        String serverName = getServerName(server);

        // Make sure we don't stop the temporary server
        if (!isWaitingServer(serverName)) {
            // Cancel the previous task so we don't have conflicting tasks
            cancelTask(server);

            logger.debug("Scheduling server '{}' to shutdown in {} seconds if empty.", serverName, delay.getSeconds());
            Scheduler.TaskBuilder taskBuilder = proxy.getScheduler()
                    .buildTask(plugin, () -> stopNowIfEmpty(server))
                    .delay(delay);
            ScheduledTask scheduledTask = taskBuilder.schedule();
            shutdownTasks.put(serverName, scheduledTask);
        }
    }

    private void stopNowIfEmpty(RegisteredServer server) {
        if (isServerEmpty(server)) {
            String serverName = getServerName(server);
            Configuration config = configurationLoader.getConfiguration();
            if (!config.getAllServers().contains(serverName)) {
                return;
            }
            if (config.getCacheMotd()) {
                motdCache.update(server);
            }
            configurationLoader.getAPI().stop(serverName);
            fr.pickaria.pterodactylpoweraction.state.ServerStateManager.setState(serverName, fr.pickaria.pterodactylpoweraction.state.ServerState.STOPPED);
        }
    }

    private boolean isWaitingServer(String serverName) {
        Optional<String> waitingServerName = configurationLoader.getConfiguration().getWaitingServerName();
        return waitingServerName.isPresent() && serverName.equals(waitingServerName.get());
    }
}
