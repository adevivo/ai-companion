package adris.altoclef.player2api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-owned settings that only the <b>server</b> is in a position to act on.
 *
 * <h2>Why this is not simply a static</h2>
 *
 * Most client-owned settings are read by the client itself — its key, its model, its corpus — so
 * they need no plumbing at all: the client's JVM loads its own file and that is the end of it. A
 * handful are different. {@code behavior.triggerPrefix} decides whether a line of chat reaches a
 * companion, and chat routing happens on the server, so a player editing it in their own config
 * would be editing a value nobody reads. That is the failure mode this codebase has hit twice
 * already: a setting that is not plumbed through does nothing, silently.
 *
 * <p>So the value travels with the roster the client announces at join, and is stored here per
 * player. The server-wide {@link BehaviorConfig#triggerPrefix} remains the fallback for anyone who
 * announced nothing — a vanilla client, or one that is out of date.
 *
 * <p>Nothing here is authoritative over the operator. These are preferences about a player's own
 * experience; the rules live in {@link ServerPolicy}.
 */
public final class PlayerPreferences {
    private PlayerPreferences() {}

    private static final Map<UUID, String> TRIGGER_PREFIX = new ConcurrentHashMap<>();

    /**
     * Record what a player's client asked for. A null or blank prefix is stored as blank, which
     * means "answer all nearby chat" — the same meaning it has in the config file, rather than
     * falling through to the server's value. A player who cleared their prefix meant to clear it.
     */
    public static void setTriggerPrefix(UUID player, String prefix) {
        if (player == null) {
            return;
        }
        TRIGGER_PREFIX.put(player, prefix == null ? "" : prefix.strip());
    }

    /** Everything this player announced, dropped when they disconnect. */
    public static void forget(UUID player) {
        if (player != null) {
            TRIGGER_PREFIX.remove(player);
        }
    }

    /**
     * Apply the speaker's own trigger prefix to what they said.
     *
     * @return the message with the prefix stripped, or null when it does not start with the prefix
     *         and should not reach a companion at all — the same contract as
     *         {@link BehaviorConfig#applyTriggerPrefix}, which this falls back to for a player whose
     *         client never announced one.
     */
    public static String applyTriggerPrefix(UUID player, String message) {
        String prefix = player == null ? null : TRIGGER_PREFIX.get(player);
        if (prefix == null) {
            return BehaviorConfig.applyTriggerPrefix(message);
        }
        if (prefix.isEmpty()) {
            return message;
        }
        if (message == null || !message.startsWith(prefix)) {
            return null;
        }
        // A bare prefix with nothing after it is an instruction to nobody, and is dropped rather
        // than delivered as an empty turn. Same rule as the server-wide path, deliberately: two
        // definitions of "addressed to the companion" would drift and nothing compares them.
        String stripped = message.substring(prefix.length()).trim();
        return stripped.isEmpty() ? null : stripped;
    }
}
