package fr.pickaria.pterodactylpoweraction.online;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PterodactylWebSocketPayload {
    PterodactylWebSocketPayload(String event, List<String> args) {
        this.event = event;
        this.args = args;
    }

    PterodactylWebSocketPayload(String event) {
        this(event, List.of());
    }

    @SerializedName("event")
    private String event;

    @SerializedName("args")
    private List<String> args;

    public String getEvent() {
        return event;
    }

    public List<String> getArgs() {
        return args;
    }
}
