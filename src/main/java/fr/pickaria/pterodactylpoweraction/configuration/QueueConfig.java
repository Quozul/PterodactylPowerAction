package fr.pickaria.pterodactylpoweraction.configuration;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages the plugin's configuration using the Configurate library.
 * This class is responsible for loading the config file from disk,
 * providing default values, and offering easy access to config values.
 */
public class QueueConfig {

    private final Path configFile;
    private final YamlConfigurationLoader loader;
    private final Logger logger;
    private CommentedConfigurationNode root;

    @Inject
    public QueueConfig(Logger logger, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.configFile = dataDirectory.resolve("queue.yml");
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .build();

        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            root = loader.load();

            if (Files.notExists(configFile.getParent())) {
                Files.createDirectories(configFile.getParent());
            }

            if (Files.notExists(configFile)) {
                logger.info("Creating a default configuration file...");
                populateDefaults();
                saveConfiguration();
            }
        } catch (IOException e) {
            logger.error("An I/O error occurred while loading the configuration", e);
            root = CommentedConfigurationNode.root();
        }
    }

    private void populateDefaults() throws SerializationException {
        root.node("queue", "enabled").set(true)
                .comment("Enable or disable the queue system entirely.");

        root.node("queue", "max-players").set(100)
                .comment("The maximum number of players allowed on the network before the queue activates.");

        root.node("queue", "limbo-server").set("limbo")
                .comment("The name of the server where players will wait. This must match a server in your velocity.toml.");

        root.node("queue", "ignored-servers").setList(String.class, List.of("auth"))
                .comment("A list of servers to ignore when calculating the total player count (e.g., an authentication server).");
    }

    private void saveConfiguration() {
        try {
            loader.save(root);
        } catch (ConfigurateException e) {
            logger.error("Unable to save the configuration file", e);
        }
    }

    // --- Getters for Configuration Values ---

    public boolean isQueueEnabled() {
        return root.node("queue", "enabled").getBoolean(true);
    }

    public int getMaxPlayers() {
        return root.node("queue", "max-players").getInt(100);
    }

    public String getLimboServerName() {
        return root.node("queue", "limbo-server").getString("limbo");
    }

    public List<String> getIgnoredServers() {
        try {
            return root.node("queue", "ignored-servers").getList(String.class, List.of());
        } catch (Exception e) {
            return List.of();
        }
    }
}
