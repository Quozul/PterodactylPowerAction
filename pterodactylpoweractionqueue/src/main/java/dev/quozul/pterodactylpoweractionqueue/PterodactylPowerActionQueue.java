package dev.quozul.pterodactylpoweractionqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.quozul.pterodactylpoweractionqueue.config.ConfigManager;
import dev.quozul.pterodactylpoweractionqueue.listeners.QueueListener;
import dev.quozul.pterodactylpoweractionqueue.queue.QueueService;
import dev.quozul.pterodactylpoweractionqueue.tasks.QueueNotifier;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "pterodactyl_power_action_queue",
        name = "PterodactylPowerActionQueue",
        version = "1.0.0",
        description = "A simple and effective queue for Velocity proxies.",
        authors = {"Quozul"}
)
public class PterodactylPowerActionQueue {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private QueueService queueService;
    private QueueNotifier queueNotifier;
    private ConfigManager configManager;

    @Inject
    public PterodactylPowerActionQueue(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 1. Initialize configuration
        this.configManager = new ConfigManager(logger, dataDirectory);

        // 2. Initialize services
        this.queueService = new QueueService();
        this.queueNotifier = new QueueNotifier(server, this, queueService);

        // 3. Register event listeners
        server.getEventManager().register(this, new QueueListener(server, queueService, configManager));

        // 4. Start repeating tasks
        this.queueNotifier.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.queueNotifier != null) {
            this.queueNotifier.stop();
        }
    }
}
