package fr.pickaria.pterodactylpoweraction.online;

import com.google.gson.Gson;
import fr.pickaria.pterodactylpoweraction.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.NoSuchElementException;

/**
 * Utility class to retrieve Pterodactyl websocket credentials.
 */
public final class PterodactylWebSocketHelper {
    private PterodactylWebSocketHelper() {
    }

    public static PterodactylWebSocketCredentialsResponse.Data getWebsocketCredentials(
            String serverIdentifier,
            Configuration configuration
    ) throws IllegalArgumentException, NoSuchElementException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(configuration.getPterodactylClientApiBaseURL().orElseThrow() + "/servers/" + serverIdentifier + "/websocket"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + configuration.getPterodactylApiKey().orElseThrow())
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Unexpected status: " + statusCode + " – " + response.body());
            }
            PterodactylWebSocketCredentialsResponse webSocketCredentials =
                    new Gson().fromJson(response.body(), PterodactylWebSocketCredentialsResponse.class);
            return webSocketCredentials.getData();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
