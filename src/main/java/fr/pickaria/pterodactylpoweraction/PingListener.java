package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.state.MotdCache;
import fr.pickaria.pterodactylpoweraction.state.ServerState;
import fr.pickaria.pterodactylpoweraction.state.ServerStateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;

public class PingListener {
    private final ConfigurationLoader configurationLoader;
    private final ProxyServer proxy;
    private final MotdCache motdCache;
    private final Logger logger;

    public PingListener(ConfigurationLoader configurationLoader, ProxyServer proxy, MotdCache motdCache, Logger logger) {
        this.configurationLoader = configurationLoader;
        this.proxy = proxy;
        this.motdCache = motdCache;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        Configuration config = configurationLoader.getConfiguration();
        if (!config.getStatePing() && !config.getCacheMotd() && !config.getStateMotd()) {
            return;
        }

        Optional<RegisteredServer> target = getServerFromEvent(event);
        if (target.isEmpty()) {
            return;
        }
        RegisteredServer server = target.get();
        String serverName = server.getServerInfo().getName();
        if (!config.getAllServers().contains(serverName)) {
            return;
        }
        ServerState state = ServerStateManager.getState(serverName);

        if (state != ServerState.STARTING) {
            try {
                boolean running = configurationLoader.getOnlineChecker(server).isRunningNow();
                if (running && state == ServerState.STOPPED) {
                    state = ServerState.RUNNING;
                    ServerStateManager.setState(serverName, state);
                } else if (!running && state == ServerState.RUNNING) {
                    state = ServerState.STOPPED;
                    ServerStateManager.setState(serverName, state);
                }
            } catch (Exception e) {
                logger.warn("Failed to check server state for {}", serverName, e);
            }
        }

        ServerPing.Builder builder = event.getPing().asBuilder();

        if (config.getStatePing()) {
            if (state == ServerState.STARTING) {
                builder.version(new ServerPing.Version(-1, coloredTranslation("state.ping.starting", NamedTextColor.GOLD)));
            } else if (state != ServerState.RUNNING) {
                builder.version(new ServerPing.Version(-1, coloredTranslation("state.ping.offline", NamedTextColor.RED)));
            }
        }

        if (config.getStateMotd()) {
            if (state == ServerState.STARTING) {
                builder.description(translatedComponent("state.motd.starting", NamedTextColor.GOLD));
            } else if (state != ServerState.RUNNING) {
                builder.description(translatedComponent("state.motd.offline", NamedTextColor.RED));
            }
        } else if (config.getCacheMotd()) {
            Optional<Component> cached = motdCache.get(serverName);
            if (cached.isPresent()) {
                builder.description(cached.get());
            } else if (state == ServerState.STARTING) {
                builder.description(translatedComponent("state.motd.starting", NamedTextColor.GOLD));
            } else if (state != ServerState.RUNNING) {
                builder.description(translatedComponent("state.motd.offline", NamedTextColor.RED));
            }
        }

        event.setPing(builder.build());
    }

    private Optional<RegisteredServer> getServerFromEvent(ProxyPingEvent event) {
        return event.getConnection().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .flatMap(host -> {
                    String[] parts = host.split("\\.");
                    String serverName = parts[0];
                    return proxy.getServer(serverName);
                });
    }

    private String coloredTranslation(String key, NamedTextColor color) {
        Component component = translatedComponent(key, color);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    private Component translatedComponent(String key, NamedTextColor color) {
        Component component = Component.translatable(key).color(color);
        return GlobalTranslator.render(component, Locale.ENGLISH);
    }
}
