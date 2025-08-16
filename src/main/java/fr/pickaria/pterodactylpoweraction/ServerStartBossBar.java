package fr.pickaria.pterodactylpoweraction;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import fr.pickaria.pterodactylpoweraction.configuration.APIType;
import fr.pickaria.pterodactylpoweraction.online.PterodactylWebSocketCredentialsResponse;
import fr.pickaria.pterodactylpoweraction.online.PterodactylWebSocketPayload;
import fr.pickaria.pterodactylpoweraction.online.PterodactylWebSocketHelper;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

class ServerStartBossBar {
    private final StartingServer audience;
    private final Configuration configuration;
    private final String serverName;
    private final Logger logger;
    private final BossBar bossBar;
    private ScheduledExecutorService scheduler;
    private WebSocket webSocket;
    private String messageKey = "bossbar.starting";
    private int dots = 0;

    private static final Duration MAX_DURATION = Duration.ofSeconds(60);

    private static final List<Phase> PHASES = List.of(
            new Phase(Pattern.compile("Updating process configuration files"), 0.05f),
            new Phase(Pattern.compile("Ensuring file permissions"), 0.10f),
            new Phase(Pattern.compile("Pulling Docker container image"), 0.20f),
            new Phase(Pattern.compile("Finished pulling Docker container image"), 0.30f),
            new Phase(Pattern.compile("Starting.*server"), 0.40f),
            new Phase(Pattern.compile("Loading properties|Loading libraries"), 0.50f),
            new Phase(Pattern.compile("Binding to host|Starting Minecraft server on"), 0.60f),
            new Phase(Pattern.compile("Initializing plugins|Loading (?:server plugin|\\d+ mods|mods)|Loaded \\d+ (?:mods|plugins)"), 0.70f),
            new Phase(Pattern.compile("Preparing (?:level|start region|spawn area|world data)"), 0.90f),
            new Phase(Pattern.compile("Done \\([0-9.]+s\\)!|Listening on"), 1.0f)
    );

    ServerStartBossBar(StartingServer audience, Configuration configuration, String serverName, Logger logger) {
        this.audience = audience;
        this.configuration = configuration;
        this.serverName = serverName;
        this.logger = logger;
        this.bossBar = BossBar.bossBar(
                Component.translatable(messageKey, Component.text(serverName)),
                0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
    }

    void start() {
        messageKey = "bossbar.starting";
        dots = 0;
        bossBar.progress(0f);
        bossBar.name(Component.translatable(messageKey, Component.text(serverName)));
        audience.showBossBar(bossBar);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::animate, 1, 1, TimeUnit.SECONDS);
        scheduler.schedule(() -> messageKey = "bossbar.taking.long", MAX_DURATION.toSeconds(), TimeUnit.SECONDS);
        scheduler.schedule(() -> {
            if (webSocket != null) webSocket.abort();
        }, MAX_DURATION.toSeconds(), TimeUnit.SECONDS);
        if (configuration.getAPIType() == APIType.PTERODACTYL) {
            startWebSocketWatcher();
        }
    }

    void addPlayer(Player player) {
        player.showBossBar(bossBar);
    }

    void stop() {
        if (webSocket != null) {
            webSocket.abort();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        audience.hideBossBar(bossBar);
        messageKey = "bossbar.starting";
        dots = 0;
        bossBar.progress(0f);
        bossBar.name(Component.translatable(messageKey, Component.text(serverName)));
    }

    void removePlayer(Player player) {
        player.hideBossBar(bossBar);
    }

    private void animate() {
        dots = (dots % 3) + 1;
        String dotsStr = ".".repeat(dots);
        Component base;
        if ("bossbar.starting".equals(messageKey)) {
            base = Component.translatable(messageKey, Component.text(serverName));
        } else {
            base = Component.translatable(messageKey);
        }
        bossBar.name(base.append(Component.text(dotsStr)));
    }

    private void handleConsole(String line) {
        for (Phase phase : PHASES) {
            if (phase.pattern.matcher(line).find()) {
                bossBar.progress(Math.max(bossBar.progress(), phase.progress));
                break;
            }
        }
    }

    private void startWebSocketWatcher() {
        try {
            String serverId = configuration.getPterodactylServerIdentifier(serverName).orElse(null);
            if (serverId == null) {
                return;
            }
            PterodactylWebSocketCredentialsResponse.Data creds =
                    PterodactylWebSocketHelper.getWebsocketCredentials(serverId, configuration);
            URI base = URI.create(configuration.getPterodactylClientApiBaseURL().orElseThrow());
            String origin = base.getScheme() + "://" + base.getHost() + (base.getPort() == -1 ? "" : ":" + base.getPort());

            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .header("Authorization", "Bearer " + configuration.getPterodactylApiKey().orElseThrow())
                    .header("Origin", origin)
                    .buildAsync(URI.create(creds.getSocket()), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket ws) {
                            webSocket = ws;
                            ws.request(1);
                            sendJson(ws, new PterodactylWebSocketPayload("auth", List.of(creds.getToken())));
                            sendJson(ws, new PterodactylWebSocketPayload("send logs"));
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            try {
                                PterodactylWebSocketPayload p = new Gson().fromJson(data.toString(), PterodactylWebSocketPayload.class);
                                if ("console output".equals(p.getEvent()) && !p.getArgs().isEmpty()) {
                                    handleConsole(p.getArgs().get(0));
                                }
                            } catch (Exception ignored) {
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            logger.error("WebSocket error", error);
                        }
                    });
        } catch (Exception e) {
            logger.error("Failed to watch console", e);
        }
    }

    private void sendJson(WebSocket ws, Object payload) {
        ws.sendText(new Gson().toJson(payload), true);
    }

    private record Phase(Pattern pattern, float progress) {
    }
}
