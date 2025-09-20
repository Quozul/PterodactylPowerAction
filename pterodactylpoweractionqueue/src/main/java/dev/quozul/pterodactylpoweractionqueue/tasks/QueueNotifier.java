package dev.quozul.pterodactylpoweractionqueue.tasks;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.quozul.pterodactylpoweractionqueue.PterodactylPowerActionQueue;
import dev.quozul.pterodactylpoweractionqueue.queue.QueueService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.TimeUnit;

/**
 * A task that periodically notifies players in the queue of their position.
 */
public class QueueNotifier {
    private final ProxyServer server;
    private final PterodactylPowerActionQueue plugin;
    private final QueueService queueService;
    private ScheduledTask task;

    public QueueNotifier(ProxyServer server, PterodactylPowerActionQueue plugin, QueueService queueService) {
        this.server = server;
        this.plugin = plugin;
        this.queueService = queueService;
    }

    public void start() {
        // Cancel any existing task to prevent duplicates
        if (this.task != null) {
            this.task.cancel();
        }

        this.task = server.getScheduler()
                .buildTask(plugin, this::notifyPlayers)
                .repeat(9L, TimeUnit.SECONDS)
                .schedule();
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }
    }

    private void notifyPlayers() {
        if (queueService.size() == 0) {
            return; // No one to notify
        }

        queueService.getQueueEntries().forEach(entry -> {
            int position = queueService.getPosition(entry.player());
            if (position != -1) {
                entry.player().sendMessage(
                        Component.text("You are currently position ", NamedTextColor.GRAY)
                                .append(Component.text(position, NamedTextColor.YELLOW))
                                .append(Component.text(" of ", NamedTextColor.GRAY))
                                .append(Component.text(queueService.size(), NamedTextColor.YELLOW))
                                .append(Component.text(" in the queue.", NamedTextColor.GRAY))
                );
            }
        });
    }
}
