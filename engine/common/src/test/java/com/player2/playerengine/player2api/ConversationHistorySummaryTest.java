package com.player2.playerengine.player2api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The history summary, which used to run a model call inline on whatever thread added a message.
 *
 * <p>For a client-brain turn that thread is the server's, so each summary froze the tick loop, and a
 * failed one dropped a single message and left the history over the limit: <b>every later message
 * made another blocking call</b>. holly logged a 401 from it on 2026-09-26. These pin that the call is
 * handed to an executor, that a failure is tried once, and that nothing said meanwhile is lost.
 */
class ConversationHistorySummaryTest {

    private static final int MAX_HISTORY = 64;
    private static final int SUMMARY_COUNT = 48;

    /** Runs the work at once, on the calling thread — a model that answers instantly. */
    private static final Executor DIRECT = Runnable::run;

    /** Holds the work until {@link #runAll}, like a model call still in flight. */
    private static final class HeldExecutor implements Executor {
        final List<Runnable> held = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            held.add(command);
        }

        void runAll() {
            List<Runnable> now = new ArrayList<>(held);
            held.clear();
            now.forEach(Runnable::run);
        }
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    /** Assistant messages cut off; user messages never do, as in the real add methods. */
    private static void add(ConversationHistory h, int i, ConversationHistory.Summarizer s, Executor e) {
        h.addHistory(msg("assistant", "m" + i), true, s, e);
    }

    private static List<String> contents(ConversationHistory h) {
        List<String> out = new ArrayList<>();
        for (JsonObject o : h.getListJSON()) {
            out.add(o.get("content").getAsString());
        }
        return out;
    }

    @Test
    @DisplayName("the oldest 48 are replaced by one system note, and the rest are kept in order")
    void summaryReplacesTheOldestChunk() {
        ConversationHistory h = new ConversationHistory("sys");
        AtomicInteger calls = new AtomicInteger();
        List<Integer> sizes = new ArrayList<>();
        ConversationHistory.Summarizer s = msgs -> {
            calls.incrementAndGet();
            sizes.add(msgs.size());
            return "notes";
        };
        for (int i = 1; i <= MAX_HISTORY; i++) {
            add(h, i, s, DIRECT);
        }

        assertEquals(1, calls.get());
        assertEquals(List.of(SUMMARY_COUNT), sizes);
        List<String> c = contents(h);
        assertEquals("sys", c.get(0));
        assertEquals("Summary of earlier events: notes", c.get(1));
        assertEquals("system", h.getListJSON().get(1).get("role").getAsString());
        assertEquals("m" + (SUMMARY_COUNT + 1), c.get(2));
        assertEquals("m" + MAX_HISTORY, c.get(c.size() - 1));
        assertEquals(2 + MAX_HISTORY - SUMMARY_COUNT, c.size());
    }

    @Test
    @DisplayName("a failed summary drops its chunk once, instead of calling again on every message")
    void failureIsNotRetriedPerMessage() {
        ConversationHistory h = new ConversationHistory("sys");
        AtomicInteger calls = new AtomicInteger();
        ConversationHistory.Summarizer failing = msgs -> {
            calls.incrementAndGet();
            throw new IllegalStateException("HTTP 401");
        };
        for (int i = 1; i <= MAX_HISTORY + 20; i++) {
            add(h, i, failing, DIRECT);
        }

        // The old code called on each of the last 21 messages. Now: once at 65, and the history is
        // then far below the limit again.
        assertEquals(1, calls.get());
        List<String> c = contents(h);
        assertEquals("sys", c.get(0));
        assertFalse(c.stream().anyMatch(x -> x.startsWith("Summary")));
        assertEquals("m" + (SUMMARY_COUNT + 1), c.get(1));
        assertEquals("m" + (MAX_HISTORY + 20), c.get(c.size() - 1));
    }

    @Test
    @DisplayName("while a summary is in flight: no second call, and later messages survive the splice")
    void inFlightKeepsLaterMessages() {
        ConversationHistory h = new ConversationHistory("sys");
        HeldExecutor held = new HeldExecutor();
        AtomicInteger calls = new AtomicInteger();
        ConversationHistory.Summarizer s = msgs -> {
            calls.incrementAndGet();
            return "notes";
        };
        for (int i = 1; i <= MAX_HISTORY + 5; i++) {
            add(h, i, s, held);
        }
        // Handed off, not run: the adding thread never waited on a model.
        assertEquals(0, calls.get());
        assertEquals(1, held.held.size());
        assertEquals(1 + MAX_HISTORY + 5, h.getListJSON().size());

        held.runAll();
        assertEquals(1, calls.get());
        add(h, MAX_HISTORY + 6, s, held);

        List<String> c = contents(h);
        assertEquals("Summary of earlier events: notes", c.get(1));
        assertEquals("m" + (SUMMARY_COUNT + 1), c.get(2));
        assertEquals("m" + (MAX_HISTORY + 6), c.get(c.size() - 1));
        assertEquals(2 + (MAX_HISTORY + 6 - SUMMARY_COUNT), c.size());
        assertTrue(held.held.isEmpty());
    }

    @Test
    @DisplayName("a summary that finishes after clear() is discarded, not spliced into the new conversation")
    void clearedWhileInFlight() {
        ConversationHistory h = new ConversationHistory("sys");
        HeldExecutor held = new HeldExecutor();
        ConversationHistory.Summarizer s = msgs -> "notes about a forgotten conversation";
        for (int i = 1; i <= MAX_HISTORY + 1; i++) {
            add(h, i, s, held);
        }
        h.clear();
        held.runAll();
        add(h, 1000, s, held);

        assertEquals(List.of("sys", "m1000"), contents(h));
    }

    @Test
    @DisplayName("user and system messages never trigger a summary")
    void onlyCutOffMessagesTrigger() {
        ConversationHistory h = new ConversationHistory("sys");
        AtomicInteger calls = new AtomicInteger();
        ConversationHistory.Summarizer s = msgs -> {
            calls.incrementAndGet();
            return "notes";
        };
        for (int i = 1; i <= MAX_HISTORY + 10; i++) {
            h.addHistory(msg("user", "u" + i), false, s, DIRECT);
        }
        assertEquals(0, calls.get());
        add(h, 1, s, DIRECT);
        assertEquals(1, calls.get());
    }
}
