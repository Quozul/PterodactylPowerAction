package fr.pickaria.pterodactylpoweraction;

import fr.pickaria.pterodactylpoweraction.configuration.ShutdownBehaviour;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Configuration {
    Map<String, Object> getRawConfig();

    ShutdownBehaviour getShutdownBehaviour();

    Optional<String> getPterodactylApiKey();

    Optional<String> getPterodactylClientApiBaseURL();

    Optional<String> getPterodactylServerIdentifier(String serverName);

    Optional<String> getWaitingServerName();

    boolean shouldStartWaitingServer();

    Duration getMaximumPingDuration();

    Duration getShutdownAfterDuration();

    boolean getRedirectToWaitingServerOnKick();

    Set<String> getAllServers();

    boolean isBossBarEnabled();

    boolean shouldCheckWhitelist(String serverName);

    boolean getStatePing();

    boolean getCacheMotd();

    boolean getStateMotd();

    record PowerCommands(Optional<String> workingDirectory, String start, String stop) {
    }
}
