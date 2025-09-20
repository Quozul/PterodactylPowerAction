package fr.pickaria.pterodactylpoweraction.listeners;

import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.messager.Messager;
import fr.pickaria.pterodactylpoweraction.configuration.ConfigurationLoader;
import fr.pickaria.pterodactylpoweractionapi.events.StartServerEvent;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;

public class WhitelistListener {

    private final Logger logger;
    private final ConfigurationLoader configurationLoader;
    private final Messager messager = new Messager();

    public WhitelistListener(
            ConfigurationLoader configurationLoader,
            Logger logger
    ) {
        this.logger = logger;
        this.configurationLoader = configurationLoader;
    }

    @Subscribe(priority = 10)
    public void onStartServer(StartServerEvent event) {
        StartServerEvent.StartServerResult result = isAllowedToStart(event.targetServer(), event.player());
        event.setResult(result);
    }

    private StartServerEvent.StartServerResult isAllowedToStart(RegisteredServer originalServer, Player player) {
        String serverName = originalServer.getServerInfo().getName();
        if (configurationLoader.getConfiguration().shouldCheckWhitelist(serverName)) {
            try {
                boolean whitelisted = configurationLoader.getAPI()
                        .isPlayerWhitelisted(serverName, player.getUsername())
                        .get();
                if (!whitelisted) {
                    notifyPlayerOrDisconnect(player, "whitelist.not.whitelisted");
                    return StartServerEvent.StartServerResult.denied();
                }
            } catch (ExecutionException | InterruptedException | JsonSyntaxException e) {
                logger.error("Failed to check whitelist for server {}", serverName, e);
                notifyPlayerOrDisconnect(player, "whitelist.verification.failed");
                return StartServerEvent.StartServerResult.denied();
            }
        }
        return StartServerEvent.StartServerResult.allowed(originalServer);
    }

    private void notifyPlayerOrDisconnect(Player player, String key) {
        if (player.getCurrentServer().isPresent()) {
            messager.error(player, key);
        } else {
            player.disconnect(Component.translatable(key));
        }
    }
}
