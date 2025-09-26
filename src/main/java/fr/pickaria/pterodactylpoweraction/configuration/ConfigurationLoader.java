package fr.pickaria.pterodactylpoweraction.configuration;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.pterodactylpoweraction.Configuration;
import fr.pickaria.pterodactylpoweraction.OnlineChecker;
import fr.pickaria.pterodactylpoweraction.PowerActionAPI;
import fr.pickaria.pterodactylpoweraction.api.PterodactylAPI;
import fr.pickaria.pterodactylpoweraction.online.PterodactylOnlineChecker;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigurationLoader {
    private static ConfigurationLoader instance;
    private final Logger logger;
    private final Path dataDirectory;
    private Configuration configuration;

    public ConfigurationLoader(Logger logger, Path dataDirectory) {
        assert instance == null;
        instance = this;

        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public Configuration getConfiguration() {
        if (configuration == null) {
            this.loadConfiguration();
        }
        return configuration;
    }

    public boolean reload() {
        return loadConfiguration();
    }

    public PowerActionAPI getAPI() throws IllegalArgumentException {
        return new PterodactylAPI(logger, getConfiguration());
    }

    public OnlineChecker getOnlineChecker(RegisteredServer server) {
        Configuration configuration = getConfiguration();
        return new PterodactylOnlineChecker(server, configuration);
    }

    /**
     * Loads the configuration and stores it. If loading fails, keeps the previous configuration.
     *
     * @return true if success
     */
    private boolean loadConfiguration() {
        // Create the dataDirectory if it does not exist
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                logger.error("Error creating data directory", e);
                return false;
            }
        }

        // Load the config.yml file from the dataDirectory
        File configurationFile = dataDirectory.resolve("config.yml").toFile();
        if (!configurationFile.exists()) {
            logger.info("Configuration file not found, creating it: {}", configurationFile.getAbsolutePath());
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configurationFile.toPath());
                } else {
                    throw new FileNotFoundException("config.yml not found in resources");
                }
            } catch (IOException e) {
                logger.error("Error creating default configuration", e);
                return false;
            }
        }

        try {
            this.configuration = new YamlConfiguration(configurationFile, logger);
            return true;
        } catch (IOException e) {
            logger.error("Error loading configuration", e);
            return false;
        }
    }
}
