package fr.pickaria.pterodactylpoweractionapi.events;

import com.velocitypowered.api.proxy.server.RegisteredServer;

public class ScheduleShutdownServerEvent {

    private final RegisteredServer targetServer;

    public ScheduleShutdownServerEvent(RegisteredServer targetServer) {
        this.targetServer = targetServer;
    }

    public RegisteredServer targetServer() {
        return targetServer;
    }
}
