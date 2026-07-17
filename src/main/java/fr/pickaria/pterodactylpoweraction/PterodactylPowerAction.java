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
import fr.pickaria.pterodactylpoweraction.commands.PterodactylPowerActionCommand;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweraction.configuration.ShutdownBehaviour;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.text.MessageFormat;
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
    private final ConfigurationLoader configurationLoader;
    private final ShutdownManager shutdownManager;

    @Inject
    public PterodactylPowerAction(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = server;
        this.logger = logger;
        this.configurationLoader = new ConfigurationLoader(logger, dataDirectory);
        this.shutdownManager = new ShutdownManager(proxy, this, configurationLoader, logger);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.initializeCommand();

        initializeTranslator(
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.FRENCH),
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.ENGLISH),
                ResourceBundle.getBundle("PterodactylPowerAction.Bundle", Locale.GERMAN)
        );

        try {
            ConnectionListener listener = new ConnectionListener(configurationLoader, proxy, logger, shutdownManager);
            proxy.getEventManager().register(this, listener);
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
        TranslationStore.StringBased<MessageFormat> store = TranslationStore.messageFormat(Key.key("pickaria:power_action"));
        for (ResourceBundle bundle : bundles) {
            store.registerAll(bundle.getLocale(), bundle, true);
        }
        store.defaultLocale(Locale.ENGLISH);
        GlobalTranslator.translator().addSource(store);
    }

    private void initializeCommand() {
        CommandManager commandManager = proxy.getCommandManager();
        PterodactylPowerActionCommand pterodactylPowerActionCommand = new PterodactylPowerActionCommand(proxy, logger, configurationLoader, shutdownManager);
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
                    }
                });
    }
}
