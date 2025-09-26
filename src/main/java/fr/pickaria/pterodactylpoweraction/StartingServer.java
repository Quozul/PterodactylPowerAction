package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.ServerStartBossBar;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class StartingServer implements ForwardingAudience {
    private final RegisteredServer server;
    private final ConfigurationLoader configurationLoader;
    private final ShutdownManager shutdownManager;
    private final Set<Player> waitingPlayers = ConcurrentHashMap.newKeySet();
    private final Logger logger;
    private final Messager messager;
    private final AtomicBoolean isStarting = new AtomicBoolean(false);
    private ServerStartBossBar bossBar;
    private static final Set<StartingServer> INSTANCES = ConcurrentHashMap.newKeySet();

    public StartingServer(RegisteredServer server, ConfigurationLoader configurationLoader, ShutdownManager shutdownManager, Logger logger, Messager messager) {
        this.server = server;
        this.configurationLoader = configurationLoader;
        this.shutdownManager = shutdownManager;
        this.logger = logger;
        this.messager = messager;
        INSTANCES.add(this);
        reloadBossBar();
    }

    /**
     * Add a player, then start the server if required.
     * If the server is already in a starting state, the player will be redirected alongside the other waiting players.
     *
     * @param player Player to add to the waiting room
     * @return `true` if the player has been added to the waiting list
     */
    public boolean addPlayer(Player player) {
        boolean added = waitingPlayers.add(player);

        if (bossBar != null && added) {
            bossBar.addPlayer(player);
        }

        if (isStarting.compareAndSet(false, true)) {
            String serverName = server.getServerInfo().getName();
            fr.pickaria.pterodactylpoweraction.state.ServerStateManager.setState(serverName, fr.pickaria.pterodactylpoweraction.state.ServerState.STARTING);
            configurationLoader.getAPI().start(serverName).whenComplete((result, exception) -> {
                if (exception == null) {
                    pingUntilUpAndRedirectPlayers();
                } else {
                    informError(exception);
                }
            });
            if (bossBar != null) {
                bossBar.start();
            }
        }

        return added;
    }

    private void pingUntilUpAndRedirectPlayers() {
        boolean hasRedirectedAtLeastOnePlayer = false;

        try {
            waitForServer();
            fr.pickaria.pterodactylpoweraction.state.ServerStateManager.setState(server.getServerInfo().getName(), fr.pickaria.pterodactylpoweraction.state.ServerState.RUNNING);

            if (configurationLoader.getConfiguration().getCacheMotd()) {
                shutdownManager.scheduleMotdCache(server, Duration.ofMinutes(1));
            }

            for (Player player : waitingPlayers) {
                if (player.isActive()) {
                    hasRedirectedAtLeastOnePlayer = redirectPlayer(player);
                }
            }

            if (!hasRedirectedAtLeastOnePlayer) {
                // If we haven't redirected a single player, check if we can stop the server again
                shutdownManager.scheduleShutdown(server);
            }
        } catch (CompletionException | CancellationException | ExecutionException | InterruptedException exception) {
            informError(exception);
        } finally {
            if (isStarting.getAndSet(false)) {
                waitingPlayers.clear();
                if (bossBar != null) {
                    bossBar.stop();
                }
            }
        }
    }

    private void informError(Throwable throwable) {
        String serverName = server.getServerInfo().getName();
        logger.error("An error occurred while starting the server '{}'", serverName, throwable);
        fr.pickaria.pterodactylpoweraction.state.ServerStateManager.setState(serverName, fr.pickaria.pterodactylpoweraction.state.ServerState.STOPPED);
        messager.error(this, "failed.to.start.server", new Text(Component.text(serverName)));
        if (bossBar != null) {
            bossBar.stop();
        }
        waitingPlayers.clear();
        isStarting.set(false);
    }

    private void waitForServer() throws ExecutionException, InterruptedException {
        configurationLoader.getOnlineChecker(server).waitForRunning().get();
    }

    private boolean redirectPlayer(Player player) {
        String serverName = server.getServerInfo().getName();
        Component serverNameComponent = Component.text(serverName);
        try {
            if (bossBar != null) {
                bossBar.removePlayer(player);
            }
            waitingPlayers.remove(player);
            ConnectionRequestBuilder.Result result = player.createConnectionRequest(server).connect().get();
            if (result.isSuccessful()) {
                return result.isSuccessful();
            } else if (configurationLoader.getConfiguration().getRedirectToWaitingServerOnKick()) {
                result.getReasonComponent().ifPresentOrElse(
                        (reason) -> messager.error(player, "failed.to.redirect.reason", new Text(serverNameComponent), new Text(reason)),
                        () -> messager.error(player, "failed.to.redirect", new Text(serverNameComponent))
                );
            } else {
                Optional<Component> reasonComponent = result.getReasonComponent();
                Component kickReason = reasonComponent.orElseGet(() -> Component.translatable("failed.to.redirect", serverNameComponent));
                player.disconnect(kickReason);
            }

        } catch (CancellationException | ExecutionException | InterruptedException exception) {
            logger.error("An error occurred while redirecting the player '{}' to the server '{}'", player.getUsername(), serverName, exception);
            messager.error(player, "failed.to.redirect", new Text(serverNameComponent));
        }
        return false;
    }

    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return waitingPlayers;
    }

    private void reloadBossBar() {
        if (bossBar != null) {
            bossBar.stop();
        }
        bossBar = configurationLoader.getConfiguration().isBossBarEnabled()
                ? new ServerStartBossBar(this, configurationLoader.getConfiguration(), server.getServerInfo().getName(), logger)
                : null;
        if (bossBar != null && isStarting.get()) {
            bossBar.start();
        }
    }

    public static void reloadAll() {
        for (StartingServer server : INSTANCES) {
            server.reloadBossBar();
        }
    }
}
