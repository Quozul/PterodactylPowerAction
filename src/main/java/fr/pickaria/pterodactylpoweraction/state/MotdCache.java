package fr.pickaria.pterodactylpoweraction.state;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MotdCache {
    private final Path file;
    private final Logger logger;
    private final Map<String, String> cache = new HashMap<>();
    private final Yaml yaml;
    private final GsonComponentSerializer serializer = GsonComponentSerializer.gson();

    public MotdCache(Path dataDirectory, Logger logger) {
        this.file = dataDirectory.resolve("motd-cache.yml");
        this.logger = logger;

        DumperOptions options = new DumperOptions();
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        this.yaml = new Yaml(options);

        load();
    }

    private void load() {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Map<String, Object> loaded = yaml.load(in);
                if (loaded != null) {
                    loaded.forEach((key, value) -> {
                        if (value instanceof String) {
                            cache.put(key, (String) value);
                        } else {
                            logger.warn("Invalid MOTD cache entry for {}", key);
                        }
                    });
                }
            } catch (IOException e) {
                logger.warn("Failed to load MOTD cache", e);
            }
        }
    }

    private void save() {
        try (Writer writer = Files.newBufferedWriter(file)) {
            yaml.dump(cache, writer);
        } catch (IOException e) {
            logger.warn("Failed to save MOTD cache", e);
        }
    }

    public void update(RegisteredServer server) {
        try {
            ServerPing ping = server.ping().get();
            Component motd = ping.getDescriptionComponent();
            cache.put(server.getServerInfo().getName(), serializer.serialize(motd));
            save();
        } catch (InterruptedException | ExecutionException e) {
            logger.warn("Failed to cache MOTD for {}", server.getServerInfo().getName(), e);
        }
    }

    public Optional<Component> get(String serverName) {
        return Optional.ofNullable(cache.get(serverName)).map(serializer::deserialize);
    }
}
