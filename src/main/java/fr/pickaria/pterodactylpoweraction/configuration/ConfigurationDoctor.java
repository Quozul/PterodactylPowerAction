package fr.pickaria.pterodactylpoweraction.configuration;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.pterodactylpoweraction.Configuration;
import fr.pickaria.pterodactylpoweraction.api.PterodactylAPI;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ConfigurationDoctor {
    private final ProxyServer proxy;
    private final Logger logger;

    public ConfigurationDoctor(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    public void validateConfig(ConfigurationLoader configurationLoader) {
        Configuration configuration = configurationLoader.getConfiguration();
        boolean isValid = true;
        Map<String, Object> config = configuration.getRawConfig();

        Optional<String> rawApiType = Optional.ofNullable(config.get("type")).map(Object::toString).map(String::toLowerCase);
        if (rawApiType.isPresent()) {
            if (!rawApiType.get().equals("pterodactyl") && !rawApiType.get().equals("shell")) {
                logger.error("Invalid API type '{}'. Must be either 'pterodactyl' or 'shell'.", rawApiType.get());
                return;
            }
        }

        Optional<String> rawPingMethod = Optional.ofNullable(config.get("ping_method")).map(Object::toString).map(String::toLowerCase);
        if (rawPingMethod.isPresent()) {
            if (!rawPingMethod.get().equals("pterodactyl") && !rawPingMethod.get().equals("ping")) {
                logger.error("Invalid ping method '{}'. Must be either 'pterodactyl' or 'ping'.", rawPingMethod.get());
                return;
            }
        }

        if (rawApiType.isPresent() && rawPingMethod.isPresent() && rawApiType.get().equals("shell") && rawPingMethod.get().equals("pterodactyl")) {
            logger.error("Shell API cannot be used with Pterodactyl ping method.");
            return;
        }

        APIType apiType = configuration.getAPIType();
        PingMethod pingMethod = configuration.getPingMethod();
        PterodactylAPI pterodactylAPI = new PterodactylAPI(logger, configuration);

        // Validate API-specific configuration
        if (apiType == APIType.PTERODACTYL || pingMethod == PingMethod.PTERODACTYL) {
            if (!config.containsKey("pterodactyl_client_api_base_url")) {
                logger.error("'pterodactyl_client_api_base_url' is missing but required when type or ping method are 'pterodactyl'.");
                isValid = false;
            }

            Optional<String> apiKeyOpt = configuration.getPterodactylApiKey();
            if (apiKeyOpt.isEmpty()) {
                logger.error("'pterodactyl_api_key' is missing but required when type or ping method are 'pterodactyl'.");
                isValid = false;
            } else if (!apiKeyOpt.get().startsWith("ptlc_")) {
                logger.error("Invalid API key. Please create an API Key from your account's page.");
                isValid = false;
            }
        }

        // Validate waiting server configuration
        Optional<String> waitingServerName = configuration.getWaitingServerName();
        if (waitingServerName.isPresent()) {
            Optional<RegisteredServer> registeredWaitingServer = proxy.getServer(waitingServerName.get());

            if (registeredWaitingServer.isEmpty()) {
                logger.warn("Waiting server '{}' is not configured in 'velocity.toml'.", waitingServerName.get());
                isValid = false;
            } else if (pingMethod == PingMethod.PTERODACTYL && configuration.getPterodactylServerIdentifier(waitingServerName.get()).isEmpty()) {
                logger.error("When using the Pterodactyl ping method, the waiting server's ID must be defined in the configuration.");
                isValid = false;
            } else if (!configurationLoader.getOnlineChecker(registeredWaitingServer.get()).isRunningNow()) {
                logger.warn("Waiting server '{}' is not reachable. Make sure it is always running and accessible.", waitingServerName.get());
                isValid = false;
            }
        }

        boolean shouldStartWaitingServer = configuration.shouldStartWaitingServer();
        if (shouldStartWaitingServer && waitingServerName.isEmpty()) {
            logger.warn("The plugin is configured to start a waiting server but no waiting server is configured.");
            isValid = false;
        }

        if (config.containsKey("servers")) {
            Object serversObject = config.get("servers");
            if (serversObject instanceof Map) {
                Map<String, Object> servers = (Map<String, Object>) serversObject;

                for (Map.Entry<String, Object> entry : servers.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (proxy.getServer(key).isEmpty()) {
                        logger.warn("The server '{}' is missing in 'velocity.toml'.", key);
                        isValid = false;
                    }

                    if (value instanceof Map serverConfig) {
                        Object whitelist = serverConfig.get("whitelist");
                        if (whitelist != null && !(whitelist instanceof Boolean)) {
                            logger.warn("'servers.{}.whitelist' must be a boolean if specified.", key);
                            isValid = false;
                        }
                    }

                    if (apiType == APIType.PTERODACTYL) {
                        String uuid = null;
                        if (value instanceof String) {
                            uuid = (String) value;
                        } else if (value instanceof Map serverConfig) {
                            Object identifier = serverConfig.get("identifier");
                            if (identifier instanceof String) {
                                uuid = (String) identifier;
                            } else {
                                logger.warn("'servers.{}.identifier' is missing or not a string.", key);
                                isValid = false;
                            }
                        } else {
                            logger.warn("The server '{}' entry must be a string or map when type is 'pterodactyl'.", key);
                            isValid = false;
                        }
                        if (uuid != null) {
                            if (!this.isUUID(uuid)) {
                                logger.warn("The identifier '{}' for server '{}' must be a valid UUID. You can find the 'Server ID' under the 'Settings' tab of your server on your Pterodactyl panel.", uuid, key);
                                isValid = false;
                            } else {
                                try {
                                    Boolean exists = pterodactylAPI.exists(key).get();
                                    if (!exists) {
                                        logger.warn("Server '{}' does not exist on Pterodactyl panel, you don't have access to it or your token is invalid.", key);
                                        isValid = false;
                                    }
                                } catch (ExecutionException | InterruptedException e) {
                                    logger.warn("An error occurred when trying to get the server '{}'.", key, e);
                                    isValid = false;
                                }
                            }
                        }
                    } else if (apiType == APIType.SHELL) {
                        if (value instanceof Map) {
                            Map<String, Object> powerCommands = (Map<String, Object>) value;
                            if (!powerCommands.containsKey("start")) {
                                logger.warn("'start' command for server '{}' is missing but required when type is 'shell'.", key);
                                isValid = false;
                            }
                            if (!powerCommands.containsKey("stop")) {
                                logger.warn("'stop' command for server '{}' is missing but required when type is 'shell'.", key);
                                isValid = false;
                            }
                            if (powerCommands.containsKey("working_directory")) {
                                String workingDirectory = (String) powerCommands.get("working_directory");
                                Path workingDirectoryPath = Paths.get(workingDirectory);
                                if (!Files.exists(workingDirectoryPath)) {
                                    logger.warn("The working directory specified for server '{}' does not exist.", key);
                                    isValid = false;
                                }
                            }
                        } else {
                            logger.warn("The server entry must be a map when type is 'shell'.");
                            isValid = false;
                        }
                    }
                }
            } else {
                logger.warn("The 'servers' property must be a map.");
                isValid = false;
            }
        }

        // Warn about missing optional configurations
        if (!config.containsKey("maximum_ping_duration")) {
            logger.warn("'maximum_ping_duration' is not provided, using the default value.");
            isValid = false;
        }
        if (!config.containsKey("shutdown_after_duration")) {
            logger.warn("'shutdown_after_duration' is not provided, using the default value.");
            isValid = false;
        }
        if (!config.containsKey("redirect_to_waiting_server_on_kick")) {
            logger.warn("'redirect_to_waiting_server_on_kick' is not provided, using the default value.");
            isValid = false;
        }

        if (isValid) {
            logger.info("Your configuration looks good!");
        }
    }

    private boolean isUUID(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
