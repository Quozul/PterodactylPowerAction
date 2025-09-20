package dev.quozul.pterodactylpoweractionqueue.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Manages the player queue. This class is responsible for adding, removing,
 * and processing players in the queue. It is thread-safe.
 */
public class QueueService implements ForwardingAudience {
    private final Queue<QueueEntry> queue = new ConcurrentLinkedQueue<>();

    /**
     * Adds a player to the queue.
     * @param player The player to add.
     * @param targetServer The server they wish to connect to.
     */
    public void addPlayer(Player player, RegisteredServer targetServer) {
        if (!isPlayerInQueue(player)) {
            queue.add(new QueueEntry(player, targetServer));
        }
    }

    /**
     * Removes a player from the queue, for instance if they disconnect.
     * @param player The player to remove.
     */
    public void removePlayer(Player player) {
        queue.removeIf(entry -> entry.player().equals(player));
    }

    /**
     * Gets the next player from the queue without removing them.
     * @return An Optional containing the next QueueEntry, or empty if the queue is empty.
     */
    public Optional<QueueEntry> peekNextPlayer() {
        return Optional.ofNullable(queue.peek());
    }

    /**
     * Gets and removes the next player from the queue.
     * @return An Optional containing the next QueueEntry, or empty if the queue is empty.
     */
    public Optional<QueueEntry> pollNextPlayer() {
        return Optional.ofNullable(queue.poll());
    }

    /**
     * Gets the position of a player in the queue.
     * @param player The player to find.
     * @return The 1-based position of the player, or -1 if not in queue.
     */
    public int getPosition(Player player) {
        int position = 0;
        for (QueueEntry entry : queue) {
            position++;
            if (entry.player().equals(player)) {
                return position;
            }
        }
        return -1;
    }

    public boolean isPlayerInQueue(Player player) {
        return queue.stream().anyMatch(entry -> entry.player().equals(player));
    }

    public int size() {
        return queue.size();
    }

    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        // This allows us to send messages to all players in the queue at once.
        return queue.stream()
                .map(QueueEntry::player)
                .collect(Collectors.toList());
    }

    public List<QueueEntry> getQueueEntries() {
        return List.copyOf(queue);
    }
}
