package fr.pickaria.pterodactylpoweraction.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerStateManager {
    private static final Map<String, ServerState> STATES = new ConcurrentHashMap<>();

    private ServerStateManager() {}

    public static void setState(String server, ServerState state) {
        STATES.put(server, state);
    }

    public static ServerState getState(String server) {
        return STATES.getOrDefault(server, ServerState.STOPPED);
    }
}
