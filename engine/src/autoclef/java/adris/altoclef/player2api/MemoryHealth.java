package adris.altoclef.player2api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Whether memory is actually working, and the one line of chat that says so when it is not.
 *
 * <h2>Why this exists</h2>
 *
 * Every failure in the memory path degrades to "no memories", which is by design — a dead embedder
 * must never break a conversation turn. But from the player's seat that is indistinguishable from a
 * companion that simply had nothing relevant to say, and it is <em>worse</em> than a companion with
 * no memory at all: asked about something it has stored, it will answer that it does not know. The
 * mechanism is invisible, so the failure is invisible, and the only place any of it was ever
 * reported was {@code latest.log}.
 *
 * <p>This is the same lesson as the trim diagnostic that had lost its number: <b>make the silent
 * path loud.</b> The difference is who it has to be loud to. A log line is for whoever is debugging
 * the mod; this is for whoever is playing it, who will otherwise conclude the feature does not work
 * and turn it off.
 *
 * <h2>What it will not do</h2>
 *
 * <b>Repeat itself.</b> An embedder that is down is down for every turn, and a warning on every turn
 * is worse than no warning — it trains the player to ignore chat from their companion, which is the
 * one channel that also carries things they need. So each distinct problem announces exactly once,
 * and stays latched until it clears.
 *
 * <p><b>Complain about a feature that is switched off.</b> {@code memory.enabled=false} is the
 * default and a deliberate choice; there is nothing wrong to report. Only a memory that is meant to
 * be working and is not gets a warning.
 *
 * <p><b>Distinguish whose problem it is.</b> The latches are process-wide, because four of the five
 * things that can go wrong are process-wide — one embedder, one config, one extraction endpoint. The
 * fifth, a corpus that will not load, belongs to one player, and on a shared world the first person
 * to speak to a companion is the one who hears about it. That is why its wording never says "your":
 * the log names the store. Scoping notices per player is the fix if this ever matters; it would mean
 * a target on {@link Notice} and a viewer argument on {@link #drain()}.
 *
 * <p><b>Warn on a single slow turn.</b> One missed embedding budget is a cold model or a GC pause,
 * not a broken feature, and the warm-up exists precisely so the first one is absorbed. Only a run of
 * them means memory has become unusable, so those count up to a threshold first.
 *
 * <h2>Recovery is reported too</h2>
 *
 * A player who is told the embedder is down will go and start it, and needs to know it took. Without
 * the second message the only confirmation available is asking the companion something and trying to
 * infer from the answer whether it remembered — which is exactly the ambiguity this class exists to
 * remove.
 *
 * <h2>Threading</h2>
 *
 * Reported from wherever a failure happens — pool threads, completion threads, the server thread —
 * and drained on the server thread by whoever holds a channel to the owner. Everything here is
 * lock-free and the latch is atomic, so two threads reporting the same problem at once still produce
 * one message.
 */
public final class MemoryHealth {

    private MemoryHealth() {}

    /** What can be wrong. One latch each — these are independent and can be true together. */
    public enum Kind {
        /** {@code memory.enabled} is on but {@code embeddings.enabled} is off. Nothing can rank. */
        EMBEDDINGS_OFF,
        /** A player's stored corpus would not load. Their memories exist but are not in play. */
        STORE_UNREADABLE,
        /** The embedding endpoint failed a call: unreachable, refusing, or answering wrongly. */
        EMBEDDER_FAILING,
        /** Recall keeps missing its budget. The memories are there; the turn cannot wait for them. */
        RECALL_TIMEOUT,
        /** Extraction keeps failing. Nothing new is being learned from conversation. */
        EXTRACTION_FAILING,
    }

    /**
     * How many in a row before a transient failure is called a broken feature.
     *
     * <p>Applies to the two kinds that legitimately fail once in normal operation. A single missed
     * embedding budget is the cold-model case the warm-up already exists to absorb, and a single
     * extraction failure is one lost fact out of a turn that was 86% likely to hold none.
     */
    static final int FAILURES_BEFORE_WARNING = 3;

    /** Cap on undelivered notices, so a drain that never comes cannot grow without bound. */
    private static final int MAX_PENDING = 6;

    /** Long endpoint errors are for the log; chat gets enough to recognise which one it is. */
    private static final int MAX_DETAIL_CHARS = 160;

    /** Currently-true problems and why, whether or not they have been announced. */
    private static final Map<Kind, String> problems = new ConcurrentHashMap<>();

    /** Problems already said out loud. {@code add}/{@code remove} are the announce-once latch. */
    private static final Set<Kind> announced = ConcurrentHashMap.newKeySet();

    /** Lines waiting for someone with a channel to the owner. */
    private static final Queue<Notice> pending = new ConcurrentLinkedQueue<>();

    /**
     * One thing to tell the owner.
     *
     * @param text     what to put in their chat
     * @param problem  true for "this is broken", false for "it is working again". Carried as a flag
     *                 rather than left for the reader to infer from the text, so the delivering side
     *                 can colour a recovery as good news instead of rendering it in the red every
     *                 other notice uses.
     */
    public record Notice(String text, boolean problem) {}

    private static final AtomicInteger consecutiveTimeouts = new AtomicInteger();
    private static final AtomicInteger consecutiveExtractionFailures = new AtomicInteger();

    // ---------------------------------------------------------------- reporting

    /** Memory is on but there is no embedder to rank with — a config contradiction, not a fault. */
    public static void embeddingsOff() {
        report(Kind.EMBEDDINGS_OFF, null);
    }

    /** Embeddings are configured on. Clears {@link Kind#EMBEDDINGS_OFF} if it was raised. */
    public static void embeddingsOn() {
        resolved(Kind.EMBEDDINGS_OFF);
    }

    /**
     * A player's corpus would not load.
     *
     * <p>Worth its own message because it is the one failure where memories exist and are not lost:
     * {@code MemoryStore.load} throws rather than discarding a torn corpus, so the file on disk is
     * still whole and the right move is to look at it, not to start again.
     */
    public static void storeUnreadable(Throwable why) {
        report(Kind.STORE_UNREADABLE, describe(why));
    }

    /** A corpus loaded. Clears {@link Kind#STORE_UNREADABLE}. */
    public static void storeLoaded() {
        resolved(Kind.STORE_UNREADABLE);
    }

    /**
     * An embedding call failed. Announced on the first one, unlike the counted kinds below: an
     * embedder that refuses a connection is not having a bad moment, and every write and every
     * recall goes through it.
     */
    public static void embedFailed(Throwable why) {
        report(Kind.EMBEDDER_FAILING, describe(why));
    }

    /** An embedding call succeeded. Clears {@link Kind#EMBEDDER_FAILING}. */
    public static void embedSucceeded() {
        resolved(Kind.EMBEDDER_FAILING);
    }

    /**
     * A recall gave up waiting for its query vector.
     *
     * <p>Counted rather than announced, and reset by any recall that completes — see
     * {@link #FAILURES_BEFORE_WARNING}.
     */
    public static void recallTimedOut() {
        if (consecutiveTimeouts.incrementAndGet() >= FAILURES_BEFORE_WARNING) {
            report(Kind.RECALL_TIMEOUT, null);
        }
    }

    /**
     * A recall completed within its budget.
     *
     * <p>"Completed" and not "found something": recalling nothing is a normal, correct outcome on a
     * turn where nothing stored was relevant. This class tracks the machinery, never the answer.
     */
    public static void recallSucceeded() {
        consecutiveTimeouts.set(0);
        resolved(Kind.RECALL_TIMEOUT);
    }

    /** An extraction call failed. Counted, for the same reason recall timeouts are. */
    public static void extractionFailed(Throwable why) {
        if (consecutiveExtractionFailures.incrementAndGet() >= FAILURES_BEFORE_WARNING) {
            report(Kind.EXTRACTION_FAILING, describe(why));
        }
    }

    /**
     * An extraction call came back and was parsed.
     *
     * <p>Again the mechanism, not the yield: extraction is <em>expected</em> to learn nothing from
     * most turns, so "stored no facts" must not read as a failure here or the counter would warn
     * about a perfectly healthy extractor.
     */
    public static void extractionSucceeded() {
        consecutiveExtractionFailures.set(0);
        resolved(Kind.EXTRACTION_FAILING);
    }

    // ---------------------------------------------------------------- delivery

    /**
     * Takes everything waiting to be said, oldest first, and empties the queue.
     *
     * <p>Call from somewhere that can put a line in the owner's chat. Returns an empty list almost
     * always, which is the point — there is nothing to say unless something changed.
     */
    public static List<Notice> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<Notice> out = new ArrayList<>();
        for (Notice line = pending.poll(); line != null; line = pending.poll()) {
            out.add(line);
        }
        return out;
    }

    /** True while anything is known to be wrong, announced or not. */
    public static boolean isDegraded() {
        return !problems.isEmpty();
    }

    /** One line for {@code CompanionMemory.stats()} and the log. */
    public static String summary() {
        if (problems.isEmpty()) {
            return "healthy";
        }
        StringBuilder sb = new StringBuilder();
        for (Kind kind : Kind.values()) {
            String detail = problems.get(kind);
            if (detail == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(kind);
            if (!detail.isBlank()) {
                sb.append(" (").append(detail).append(')');
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- internals

    private static void report(Kind kind, String detail) {
        // Nothing is wrong with a feature that is switched off. Guarded here rather than at every
        // call site so a future reporter cannot forget it.
        if (!MemoryConfig.enabled) {
            return;
        }
        problems.put(kind, detail == null ? "" : detail);
        if (announced.add(kind)) {
            stage(new Notice(warningFor(kind, detail), true));
        }
    }

    private static void resolved(Kind kind) {
        problems.remove(kind);
        // Only speak if this was actually announced. Clearing something nobody was told about must
        // stay silent, or every healthy startup would open with news of a recovery from nothing.
        if (announced.remove(kind)) {
            stage(new Notice(recoveryFor(kind), false));
        }
    }

    private static void stage(Notice line) {
        pending.add(line);
        while (pending.size() > MAX_PENDING) {
            pending.poll();
        }
    }

    private static String warningFor(Kind kind, String detail) {
        String because = detail == null || detail.isBlank() ? "" : " (" + detail + ")";
        switch (kind) {
            case EMBEDDINGS_OFF:
                return "⚠ Memory is on but embeddings are off, so nothing can be stored or "
                        + "recalled. Set embeddings.enabled to true in /companion config, then run "
                        + "/companion reload.";
            case STORE_UNREADABLE:
                // Deliberately not "YOUR memories": the latch is process-wide (see the class docs)
                // and on a shared world the reader may not be whose store failed. Saying "your" to
                // the wrong player would send them off to re-teach a companion that is fine.
                return "⚠ Saved memories could not be loaded, so a companion is starting from "
                        + "nothing this session. They were not deleted or overwritten — the log says "
                        + "whose store it is and what is wrong with it." + because;
            case EMBEDDER_FAILING:
                return "⚠ Memory is unavailable — the embedder at " + EmbeddingsConfig.baseUrl
                        + " is not answering" + because + ". Nothing will be recalled or learned "
                        + "until it is back. /companion reload once it is running.";
            case RECALL_TIMEOUT:
                return "⚠ Memory is too slow to use — the last " + FAILURES_BEFORE_WARNING
                        + " recalls ran out of their " + MemoryConfig.embedBudgetMs + "ms window, so "
                        + "your companion is answering without them. Your memories are safe.";
            case EXTRACTION_FAILING:
                return "⚠ Your companion has stopped learning from conversation — the last "
                        + FAILURES_BEFORE_WARNING + " attempts failed" + because + ". Anything you "
                        + "teach it with /companion remember still works.";
            default:
                return "⚠ Memory is not working.";
        }
    }

    private static String recoveryFor(Kind kind) {
        switch (kind) {
            case EMBEDDINGS_OFF:
                return "✔ Embeddings are on — memory is working again.";
            case STORE_UNREADABLE:
                return "✔ Saved memories loaded this time.";
            case EMBEDDER_FAILING:
                return "✔ The embedder is answering again — memory is back.";
            case RECALL_TIMEOUT:
                return "✔ Memory recall is keeping up again.";
            case EXTRACTION_FAILING:
                return "✔ Your companion is learning from conversation again.";
            default:
                return "✔ Memory is working again.";
        }
    }

    /**
     * The short form of a failure: the useful half of a stack trace, for a chat line.
     *
     * <p>Prefers the root cause's message, because the outer frame of an async failure is usually
     * {@code CompletionException} wrapping the only sentence anybody wants to read.
     */
    private static String describe(Throwable why) {
        if (why == null) {
            return null;
        }
        Throwable root = why;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String text = message == null || message.isBlank()
                ? root.getClass().getSimpleName()
                : root.getClass().getSimpleName() + ": " + message;
        text = text.replace('\n', ' ').strip();
        return text.length() <= MAX_DETAIL_CHARS
                ? text
                : text.substring(0, MAX_DETAIL_CHARS - 1) + "…";
    }

    /**
     * Puts every latch, counter and queue back to a freshly-started process.
     *
     * <p>Called by {@code /companion reload}, on the same reasoning as {@code TTSManager
     * .clearUnavailable()}: reload is the player saying "I have fixed it, try again". Without this,
     * a problem that was announced once and never cleared stays latched forever — so someone who
     * restarts their embedder, reloads, and gets it wrong a second time would be told nothing at
     * all, and the silence would look exactly like success.
     *
     * <p>Anything still broken re-announces on its next attempt, which for the config kinds is the
     * {@code CompanionMemory.warm()} call that immediately follows.
     */
    public static void rearm() {
        problems.clear();
        announced.clear();
        pending.clear();
        consecutiveTimeouts.set(0);
        consecutiveExtractionFailures.set(0);
    }
}
