package fr.pickaria.pterodactylpoweraction.configuration;

import fr.pickaria.pterodactylpoweraction.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public class YamlConfiguration implements Configuration {
    private final Map<String, Object> config;
    private final Logger logger;

    private static final Duration DEFAULT_SHUTDOWN_AFTER_DURATION = Duration.ofHours(1);
    private static final Duration DEFAULT_MAXIMUM_PING_DURATION = Duration.ofMinutes(1);
    private static final boolean DEFAULT_REDIRECT_TO_WAITING_SERVER_ON_KICK = false;
    private static final boolean DEFAULT_START_WAITING_SERVER = true;
    private static final PingMethod DEFAULT_PING_METHOD = PingMethod.PING;

    public YamlConfiguration(File file, Logger logger) throws IOException {
        this.logger = logger;

        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(file)) {
            this.config = yaml.load(is);
        }
    }

    @Override
    public Map<String, Object> getRawConfig() {
        return config;
    }

    @Override
    public APIType getAPIType() throws IllegalArgumentException {
        String type = getRequired("type", String.class);
        return APIType.valueOf(type.toUpperCase());
    }

    @Override
    public ShutdownBehaviour getShutdownBehaviour() {
        return get("shutdown_behaviour", String.class)
                .map(String::toUpperCase)
                .map(ShutdownBehaviour::valueOf)
                .orElse(ShutdownBehaviour.SHUTDOWN_ALL);
    }

    @Override
    public Optional<String> getPterodactylApiKey() {
        return get("pterodactyl_api_key", String.class);
    }

    @Override
    public Optional<String> getPterodactylClientApiBaseURL() {
        return get("pterodactyl_client_api_base_url", String.class)
                .map(YamlConfiguration::removeTrailingSlash);
    }

    @Override
    public Optional<String> getPterodactylServerIdentifier(String serverName) {
        try {
            Object configuration = getServerConfiguration(serverName);
            if (configuration instanceof String) {
                return Optional.of((String) configuration);
            } else if (configuration instanceof Map serverConfiguration) {
                Object identifier = serverConfiguration.get("identifier");
                if (identifier instanceof String) {
                    return Optional.of((String) identifier);
                }
            }
        } catch (NoSuchElementException e) {
            // Fall through to return empty
        }
        return Optional.empty();
    }

    @Override
    public @NotNull Optional<String> getWaitingServerName() {
        return get("waiting_server_name", String.class);
    }

    @Override
    public boolean shouldStartWaitingServer() {
        if (!hasWaitingServer()) {
            return false;
        }
        return getBoolean("start_waiting_server_on_startup", DEFAULT_START_WAITING_SERVER);
    }

    @Override
    public PingMethod getPingMethod() {
        return get("ping_method", String.class)
                .map(String::toUpperCase)
                .map(PingMethod::valueOf)
                .orElse(DEFAULT_PING_METHOD);
    }

    @Override
    public Duration getMaximumPingDuration() {
        return getDuration("maximum_ping_duration", DEFAULT_MAXIMUM_PING_DURATION);
    }

    @Override
    public Duration getShutdownAfterDuration() {
        return getDuration("shutdown_after_duration", DEFAULT_SHUTDOWN_AFTER_DURATION);
    }

    @Override
    public boolean getRedirectToWaitingServerOnKick() {
        if (!hasWaitingServer()) {
            return false;
        }
        return getBoolean("redirect_to_waiting_server_on_kick", DEFAULT_REDIRECT_TO_WAITING_SERVER_ON_KICK);
    }

    @Override
    public Set<String> getAllServers() {
        return getServerMap().keySet();
    }

    @Override
    public boolean shouldCheckWhitelist(String serverName) {
        try {
            Object configuration = getServerConfiguration(serverName);
            if (configuration instanceof Map serverConfiguration) {
                Object whitelist = serverConfiguration.get("whitelist");
                if (whitelist instanceof Boolean) {
                    return (Boolean) whitelist;
                }
            }
        } catch (NoSuchElementException ignored) {
        }
        return false;
    }

    @Override
    public Optional<PowerCommands> getPowerCommands(String serverName) {
        try {
            Map<String, Object> serverConfiguration = (Map<String, Object>) getServerConfiguration(serverName);

            if (!serverConfiguration.containsKey("start")) {
                logger.error("'servers.{}.start' is missing from the configuration file", serverName);
                return Optional.empty();
            }

            if (!serverConfiguration.containsKey("stop")) {
                logger.error("'servers.{}.stop' is missing from the configuration file", serverName);
                return Optional.empty();
            }

            Optional<String> workingDirectory = Optional.ofNullable((String) serverConfiguration.get("working_directory"));
            String startCommands = (String) serverConfiguration.get("start");
            String stopCommands = (String) serverConfiguration.get("stop");

            return Optional.of(new PowerCommands(workingDirectory, startCommands, stopCommands));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> getServerMap() {
        return (Map<String, Object>) config.get("servers");
    }

    private @NotNull Object getServerConfiguration(String serverName) throws NoSuchElementException {
        Map<String, Object> servers = getServerMap();
        if (servers.containsKey(serverName)) {
            return servers.get(serverName);
        }
        throw new NoSuchElementException("Server " + serverName + " not found in the configuration");
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return get(key, Integer.class).map(Duration::ofSeconds).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key, Boolean.class).orElse(defaultValue);
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = config.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        } else if (value == null) {
            logger.warn("Key '{}' has wrong type, expected {} but got null", key, type.getSimpleName());
        } else {
            logger.warn("Key '{}' has wrong type, expected {} but got {}", key, type.getSimpleName(), value.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    public <T> T getRequired(String key, Class<T> type) throws NoSuchElementException, ClassCastException {
        return get(key, type)
                .orElseThrow(() -> new NoSuchElementException("Key " + key + " not found or wrong type"));
    }

    private boolean hasWaitingServer() {
        return getWaitingServerName().isPresent();
    }

    private static @NotNull String removeTrailingSlash(@NotNull String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
