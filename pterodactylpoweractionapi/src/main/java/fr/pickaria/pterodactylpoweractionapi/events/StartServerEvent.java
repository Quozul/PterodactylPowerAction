package fr.pickaria.pterodactylpoweractionapi.events;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

public class StartServerEvent implements ResultedEvent<StartServerEvent.StartServerResult> {

    private final Player player;
    private final RegisteredServer targetServer;
    private final boolean isAlreadyConnected;

    private StartServerResult result = StartServerResult.denied();

    public StartServerEvent(Player player, RegisteredServer targetServer, boolean isAlreadyConnected) {
        this.player = player;
        this.targetServer = targetServer;
        this.isAlreadyConnected = isAlreadyConnected;
    }

    public Player player() {
        return player;
    }

    public RegisteredServer targetServer() {
        return targetServer;
    }

    public boolean isAlreadyConnected() {
        return isAlreadyConnected;
    }

    @Override
    public StartServerResult getResult() {
        return result;
    }

    @Override
    public void setResult(StartServerResult result) {
        this.result = Objects.requireNonNull(result);
    }

    public static class StartServerResult implements ResultedEvent.Result {

        private static final StartServerResult DENIED = new StartServerResult(null);

        private final RegisteredServer server;

        private StartServerResult(@Nullable RegisteredServer server) {
            this.server = server;
        }

        public static StartServerResult denied() {
            return DENIED;
        }

        public static StartServerResult allowed(RegisteredServer server) {
            Preconditions.checkNotNull(server, "server");
            return new StartServerResult(server);
        }

        public Optional<RegisteredServer> getServer() {
            return Optional.ofNullable(server);
        }

        public boolean isAllowed() {
            return server != null;
        }
    }
}
