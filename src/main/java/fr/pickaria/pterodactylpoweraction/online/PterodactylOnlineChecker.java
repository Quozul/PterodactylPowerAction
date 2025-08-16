package fr.pickaria.pterodactylpoweraction.online;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import fr.pickaria.pterodactylpoweraction.Configuration;
import fr.pickaria.pterodactylpoweraction.OnlineChecker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class PterodactylOnlineChecker implements OnlineChecker {
    private final RegisteredServer server;
    private final Configuration configuration;

    public PterodactylOnlineChecker(RegisteredServer server, Configuration configuration) {
        this.server = server;
        this.configuration = configuration;
    }

    @Override
    public CompletableFuture<Void> waitForRunning() {
        return checkServerStatusViaWebSocket(true);
    }

    @Override
    public boolean isRunningNow() {
        try {
            checkServerStatusViaWebSocket(false).get();
            return true;
        } catch (ExecutionException | InterruptedException | CancellationException ignored) {
            return false;
        }
    }

    /**
     * Checks server status using WebSocket
     *
     * @param waitUntilRunning if true, will wait until the server is running; if false, will check current status
     * @return CompletableFuture that completes when the server is running or with the current status
     */
    private CompletableFuture<Void> checkServerStatusViaWebSocket(boolean waitUntilRunning) throws NoSuchElementException, IllegalArgumentException {
        String serverId = configuration
                .getPterodactylServerIdentifier(server.getServerInfo().getName())
                .orElseThrow(() -> new NoSuchElementException("No Pterodactyl server id for " + server.getServerInfo().getName()));
        PterodactylWebSocketCredentialsResponse.Data websocketCredentials =
                PterodactylWebSocketHelper.getWebsocketCredentials(serverId, configuration);

        URI base = URI.create(configuration.getPterodactylClientApiBaseURL().orElseThrow(() -> new IllegalStateException("No base URL")));
        String origin = base.getScheme() + "://" + base.getHost() + (base.getPort() == -1 ? "" : ":" + base.getPort());

        CompletableFuture<Void> result = new CompletableFuture<>();

        Duration timeout = configuration.getMaximumPingDuration();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Schedule the timeout to fire an exception if we don't complete in time.
        ScheduledFuture<?> timeoutTask = scheduler.schedule(
                () -> result.completeExceptionally(new CompletionException(new TimeoutException("Timed out waiting for server status"))),
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
        );

        AtomicReference<WebSocket> webSocketReference = new AtomicReference<>();

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Authorization", "Bearer " + configuration.getPterodactylApiKey().orElseThrow())
                .header("Origin", origin)
                .buildAsync(URI.create(websocketCredentials.getSocket()), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocketReference.set(webSocket);
                        requestMore(webSocket);
                        sendJson(webSocket, new PterodactylWebSocketPayload("auth", List.of(websocketCredentials.getToken())));
                        sendJson(webSocket, new PterodactylWebSocketPayload("send stats"));
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        try {
                            PterodactylWebSocketPayload p = new Gson().fromJson(data.toString(), PterodactylWebSocketPayload.class);
                            if ("status".equals(p.getEvent()) && !p.getArgs().isEmpty()) {
                                String status = p.getArgs().get(0);

                                if ("running".equalsIgnoreCase(status)) {
                                    // Completed successfully: server is running
                                    if (result.complete(null)) {
                                        timeoutTask.cancel(false);
                                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                                    }
                                } else if (!waitUntilRunning) {
                                    // If we're just checking status, complete with exception if not running
                                    result.completeExceptionally(new CompletionException(new IllegalStateException("Server status: " + status)));
                                    timeoutTask.cancel(false);
                                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        requestMore(webSocket);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable err) {
                        result.completeExceptionally(err);
                        timeoutTask.cancel(false);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                        if (!result.isDone()) {
                            result.completeExceptionally(new CompletionException(new IllegalStateException("WebSocket closed before server status was determined")));
                            timeoutTask.cancel(false);
                        }
                        return WebSocket.Listener.super.onClose(ws, status, reason);
                    }

                    private void requestMore(WebSocket ws) {
                        ws.request(1);
                    }

                    private void sendJson(WebSocket ws, Object payload) {
                        ws.sendText(new Gson().toJson(payload), true);
                    }
                })
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        result.completeExceptionally(err);
                        timeoutTask.cancel(false);
                    }
                });

        result.whenComplete((ok, err) -> {
            WebSocket ws = webSocketReference.get();
            if (ws != null) ws.abort();
            scheduler.shutdown();
        });

        return result;
    }

    // Websocket credentials retrieval moved to PterodactylWebSocketHelper
}
