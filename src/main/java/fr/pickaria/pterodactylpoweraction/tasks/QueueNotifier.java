package fr.pickaria.pterodactylpoweraction.tasks;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import fr.pickaria.messager.Messager;
import fr.pickaria.messager.components.Text;
import fr.pickaria.pterodactylpoweraction.PterodactylPowerAction;
import fr.pickaria.pterodactylpoweraction.queue.QueueService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.TimeUnit;

/**
 * A task that periodically notifies players in the queue of their position.
 */
public class QueueNotifier {
    private final ProxyServer server;
    private final PterodactylPowerAction plugin;
    private final QueueService queueService;
    private final Messager messager;
    private ScheduledTask task;

    public QueueNotifier(ProxyServer server, PterodactylPowerAction plugin, QueueService queueService, Messager messager) {
        this.server = server;
        this.plugin = plugin;
        this.queueService = queueService;
        this.messager = messager;
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
                messager.info(entry.player(), "queue.position.in.queue", new Text(Component.text(position)), new Text(Component.text(queueService.size())));
            }
        });
    }
}
