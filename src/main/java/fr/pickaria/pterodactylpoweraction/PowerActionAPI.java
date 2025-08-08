package fr.pickaria.pterodactylpoweraction;

import java.util.concurrent.CompletableFuture;

public interface PowerActionAPI {
    CompletableFuture<Void> stop(String server);

    CompletableFuture<Void> start(String server);

    CompletableFuture<Boolean> isPlayerWhitelisted(String server, String playerName);
}
