package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

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
    private final AtomicBoolean isStarting = new AtomicBoolean(false);

    public StartingServer(RegisteredServer server, ConfigurationLoader configurationLoader, ShutdownManager shutdownManager, Logger logger) {
        this.server = server;
        this.configurationLoader = configurationLoader;
        this.shutdownManager = shutdownManager;
        this.logger = logger;
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

        if (isStarting.compareAndSet(false, true)) {
            String serverName = server.getServerInfo().getName();
            configurationLoader.getAPI().start(serverName).whenComplete((_, exception) -> {
                if (exception == null) {
                    pingUntilUpAndRedirectPlayers();
                } else {
                    informError(exception);
                }
            });
        }

        return added;
    }

    private void pingUntilUpAndRedirectPlayers() {
        boolean hasRedirectedAtLeastOnePlayer = false;

        try {
            waitForServer();

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
            isStarting.set(false);
            waitingPlayers.clear();
        }
    }

    private void informError(Throwable throwable) {
        String serverName = server.getServerInfo().getName();
        logger.error("An error occurred while starting the server '{}'", serverName, throwable);
        MessagesHelper.error(this, "failed.to.start.server", Component.text(serverName));
    }

    private void waitForServer() throws ExecutionException, InterruptedException {
        configurationLoader.getOnlineChecker(server).waitForRunning().get();
    }

    private boolean redirectPlayer(Player player) {
        String serverName = server.getServerInfo().getName();
        Component serverNameComponent = Component.text(serverName);
        try {
            ConnectionRequestBuilder.Result result = player.createConnectionRequest(server).connect().get();
            if (result.isSuccessful()) {
                return result.isSuccessful();
            } else if (configurationLoader.getConfiguration().getRedirectToWaitingServerOnKick()) {
                result.getReasonComponent().ifPresentOrElse(
                        (reason) -> MessagesHelper.error(player, "failed.to.redirect.reason", serverNameComponent, reason),
                        () -> MessagesHelper.error(player, "failed.to.redirect", serverNameComponent)
                );
            } else {
                Optional<Component> reasonComponent = result.getReasonComponent();
                Component kickReason = reasonComponent.orElseGet(() -> Component.translatable("failed.to.redirect", serverNameComponent));
                player.disconnect(kickReason);
            }

        } catch (CancellationException | ExecutionException | InterruptedException exception) {
            logger.error("An error occurred while redirecting the player '{}' to the server '{}'", player.getUsername(), serverName, exception);
            MessagesHelper.error(player, "failed.to.redirect", serverNameComponent);
        }
        return false;
    }

    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return waitingPlayers;
    }
}
