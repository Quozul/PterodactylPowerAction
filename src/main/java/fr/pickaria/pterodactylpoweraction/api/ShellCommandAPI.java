package fr.pickaria.pterodactylpoweraction.api;

import fr.pickaria.pterodactylpoweraction.Configuration;
import fr.pickaria.pterodactylpoweraction.PowerActionAPI;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ShellCommandAPI implements PowerActionAPI {
    private final Logger logger;
    private final Configuration configuration;

    public ShellCommandAPI(Logger logger, Configuration configuration) {
        this.logger = logger;
        this.configuration = configuration;
    }

    @Override
    public CompletableFuture<Void> stop(String server) {
        Optional<Configuration.PowerCommands> powerCommands = configuration.getPowerCommands(server);
        if (powerCommands.isEmpty()) {
            return CompletableFuture.failedFuture(new RuntimeException("No commands available for server " + server));
        }

        Configuration.PowerCommands powerCommand = powerCommands.get();
        logger.info("Stopping server {}", server);
        return runCommands(powerCommand.workingDirectory(), powerCommand.stop());
    }

    @Override
    public CompletableFuture<Void> start(String server) {
        Optional<Configuration.PowerCommands> powerCommands = configuration.getPowerCommands(server);
        if (powerCommands.isEmpty()) {
            return CompletableFuture.failedFuture(new RuntimeException("No commands available for server " + server));
        }

        Configuration.PowerCommands powerCommand = powerCommands.get();
        logger.info("Starting server {}", server);
        return runCommands(powerCommand.workingDirectory(), powerCommand.start());
    }

    @Override
    public CompletableFuture<Boolean> isPlayerWhitelisted(String server, String playerName) {
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Void> runCommands(Optional<String> workingDirectory, String command) {
        return CompletableFuture.runAsync(() -> {
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            workingDirectory.ifPresent((value) -> pb.directory(new File(value)));
            try {
                pb.start().onExit().get();
            } catch (IOException | ExecutionException | InterruptedException e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
}
