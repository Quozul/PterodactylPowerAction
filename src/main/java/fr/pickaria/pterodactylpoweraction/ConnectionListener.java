package fr.pickaria.pterodactylpoweraction;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import fr.pickaria.pterodactylpoweraction.listeners.PowerAction;
import fr.pickaria.pterodactylpoweraction.listeners.Queue;

public class ConnectionListener {
    private final PowerAction powerAction;
    private final Queue queue;

    ConnectionListener(
            PowerAction powerAction,
            Queue queue
    ) {
        this.powerAction = powerAction;
        this.queue = queue;
    }

    @Subscribe()
    public void onServerConnected(ServerConnectedEvent event) {
        powerAction.onServerConnected(event);
    }

    @Subscribe()
    public void onServerPreConnect(ServerPreConnectEvent event) {
        queue.onServerPreConnect(event);
        if (!queue.isInQueue(event.getPlayer())) {
            powerAction.onServerPreConnect(event);
        }
    }


    @Subscribe()
    public void onDisconnect(DisconnectEvent event) {
        queue.onDisconnect(event);
        powerAction.onDisconnect(event);
    }

    @Subscribe()
    public void onKicked(KickedFromServerEvent event) {
        powerAction.onKicked(event);
    }
}
