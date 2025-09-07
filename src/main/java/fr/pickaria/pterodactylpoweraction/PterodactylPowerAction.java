package fr.pickaria.pterodactylpoweraction;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.pickaria.messager.Messager;
import fr.pickaria.pterodactylpoweraction.commands.PterodactylPowerActionCommand;
import fr.pickaria.pterodactylpoweraction.configuration.QueueConfig;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.configuration.ShutdownBehaviour;
import fr.pickaria.pterodactylpoweraction.listeners.PowerAction;
import fr.pickaria.pterodactylpoweraction.listeners.Queue;
import fr.pickaria.pterodactylpoweraction.queue.QueueService;
import fr.pickaria.pterodactylpoweraction.state.MotdCache;
import fr.pickaria.pterodactylpoweraction.tasks.QueueNotifier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;

@Plugin(
        id = "pterodactyl_power_action",
        name = "PterodactylPowerAction",
        version = BuildConstants.VERSION,
        authors = {"Quozul"}
)
public class PterodactylPowerAction {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfigurationLoader configurationLoader;
    private final ShutdownManager shutdownManager;
    private final MotdCache motdCache;
    private final Messager messager;

    @Inject
    public PterodactylPowerAction(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configurationLoader = new ConfigurationLoader(logger, dataDirectory);
        this.motdCache = new MotdCache(dataDirectory, logger);
        this.shutdownManager = new ShutdownManager(proxy, this, configurationLoader, logger, motdCache);
        this.messager = Messager.builder().build();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.initializeCommand();

        initializeTranslator(
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.FRENCH, UTF8ResourceBundleControl.get()),
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.ENGLISH, UTF8ResourceBundleControl.get()),
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.GERMAN, UTF8ResourceBundleControl.get())
        );

        try {
            PowerAction powerAction = new PowerAction(configurationLoader, proxy, logger, shutdownManager, messager);
            QueueConfig configManager = new QueueConfig(logger, dataDirectory);
            QueueService queueService = new QueueService();
            QueueNotifier queueNotifier = new QueueNotifier(proxy, this, queueService, messager);
            Queue queue = new Queue(proxy, queueService, configManager, messager);

            ConnectionListener listener = new ConnectionListener(powerAction, queue);
            proxy.getEventManager().register(this, listener);
            PingListener pingListener = new PingListener(configurationLoader, proxy, motdCache, logger);
            proxy.getEventManager().register(this, pingListener);
            queueNotifier.start();
        } catch (NoSuchElementException e) {
            logger.error("Error loading listener", e);
        } catch (IllegalArgumentException e) {
            logger.error("Cannot load the configuration file", e);
        }

        boolean shouldStartWaitingServer = configurationLoader.getConfiguration().shouldStartWaitingServer();
        if (shouldStartWaitingServer) {
            startWaitingServer();
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        ShutdownBehaviour shutdownBehaviour = configurationLoader.getConfiguration().getShutdownBehaviour();
        shutdownManager.shutdownAll(shutdownBehaviour, Duration.ZERO);
    }

    private void initializeTranslator(ResourceBundle... bundles) {
        TranslationRegistry registry = TranslationRegistry.create(Key.key("pickaria:power_action"));
        for (ResourceBundle bundle : bundles) {
            registry.registerAll(bundle.getLocale(), bundle, true);
        }
        registry.defaultLocale(Locale.ENGLISH);
        GlobalTranslator.translator().addSource(registry);
    }

    private void initializeCommand() {
        CommandManager commandManager = proxy.getCommandManager();
        PterodactylPowerActionCommand pterodactylPowerActionCommand = new PterodactylPowerActionCommand(proxy, logger, configurationLoader, shutdownManager, messager);
        BrigadierCommand commandToRegister = pterodactylPowerActionCommand.createBrigadierCommand();
        commandManager.register(pterodactylPowerActionCommand.getCommandMeta(commandManager, this), commandToRegister);
    }

    private void startWaitingServer() {
        configurationLoader.getConfiguration().getWaitingServerName()
                .flatMap(proxy::getServer)
                .ifPresent(server -> {
                    OnlineChecker onlineChecker = configurationLoader.getOnlineChecker(server);
                    if (!onlineChecker.isRunningNow()) {
                        PowerActionAPI api = configurationLoader.getAPI();
                        api.start(server.getServerInfo().getName());
                        shutdownManager.scheduleMotdCache(server, Duration.ofMinutes(1));
                    }
                });
    }
}
