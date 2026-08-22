package adris.altoclef.player2api;

import adris.altoclef.player2api.utils.HTTPUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turns text into a query vector, against an OpenAI-compatible {@code /v1/embeddings} endpoint.
 *
 * <p>This is the input side of retrieval and nothing consumes it yet. It exists ahead of storage and
 * the write path because it is the one part of the memory work whose unknowns can be settled by
 * asking a real server: what width the vectors are, and what a call costs in wall-clock time on the
 * machine the mod actually runs on. Both are recorded here rather than in a throwaway script, so the
 * numbers keep being true as the endpoint changes.
 *
 * <p>See {@link EmbeddingsConfig} for why this does not share a server with the brain, and why the
 * dimension is treated as a wire-format property rather than a tunable.
 *
 * <p><b>Never call {@link #embed} on the server thread.</b> It performs blocking network I/O. Use
 * {@link #embedAsync}, which runs on a small dedicated pool.
 */
public final class EmbeddingsService {
    private static final Logger LOGGER = LogManager.getLogger();

    private EmbeddingsService() {}

    /** Bounded, daemon, lazily created — a player with embeddings off never spawns a thread. */
    private static volatile ExecutorService pool;

    /** Latency accounting. The point of this class right now is that these numbers exist. */
    private static final AtomicLong calls = new AtomicLong();
    private static final AtomicLong failures = new AtomicLong();
    private static final AtomicLong totalMillis = new AtomicLong();
    private static final AtomicLong maxMillis = new AtomicLong();
    private static final AtomicLong minMillis = new AtomicLong(Long.MAX_VALUE);

    /** Set once, on the first successful call, so the observed width is logged exactly once. */
    private static final AtomicBoolean dimensionLogged = new AtomicBoolean();

    /** Set once, by the first {@link #warmUp()}, so the model load is paid for exactly once. */
    private static final AtomicBoolean warmed = new AtomicBoolean();

    /**
     * What {@link #warmUp()} embeds. Deliberately not a plausible player turn: the vector is thrown
     * away, and anything turn-shaped here would be confusing to find in a log next to real recalls.
     */
    private static final String WARM_TEXT = "warmup";

    /** Width the server actually returned, or 0 before the first success. */
    private static volatile int observedDimension = 0;

    /**
     * Embed one string, blocking until the server answers.
     *
     * @param text the text to embed; must be non-blank
     * @return the embedding, in the server's own scaling — the memory engine normalises query vectors
     *         itself, so callers need not
     * @throws IllegalStateException if embeddings are disabled, or the server returns a vector of a
     *         width this build was not measured against
     * @throws Exception on transport failure, a non-200 response, or an unparseable body
     */
    public static float[] embed(String text) throws Exception {
        return embed(text, true);
    }

    /**
     * @param measured whether this call belongs in the latency stats. False only for {@link #warmUp()}:
     *                 a warm-up is not a turn, and letting a model load land in {@code max} would put
     *                 a number in {@link #stats()} that no retrieval ever paid.
     */
    private static float[] embed(String text, boolean measured) throws Exception {
        if (!EmbeddingsConfig.enabled) {
            throw new IllegalStateException(
                    "Embeddings are disabled. Set embeddings.enabled=true in the config, "
                            + "or AICOMPANION_EMBEDDINGS_ENABLED=true.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Refusing to embed blank text.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("input", text);
        if (EmbeddingsConfig.model != null && !EmbeddingsConfig.model.isBlank()) {
            body.addProperty("model", EmbeddingsConfig.model);
        }

        Map<String, String> headers = null;
        if (EmbeddingsConfig.apiKey != null && !EmbeddingsConfig.apiKey.isBlank()) {
            headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + EmbeddingsConfig.apiKey);
        }

        long startedAt = System.nanoTime();
        Map<String, JsonElement> response;
        try {
            response = HTTPUtils.sendRequest(
                    EmbeddingsConfig.baseUrl, "/v1/embeddings", true, body, headers,
                    EmbeddingsConfig.connectTimeoutMs, EmbeddingsConfig.timeoutMs);
        } catch (Exception e) {
            failures.incrementAndGet();
            // Reported on the FIRST failure rather than counted up to a threshold: an endpoint that
            // refuses a connection is not having a bad moment, and every recall and every write
            // goes through here. See MemoryHealth.
            MemoryHealth.embedFailed(e);
            throw e;
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        float[] vector;
        try {
            vector = extractVector(response);
        } catch (Exception e) {
            failures.incrementAndGet();
            MemoryHealth.embedFailed(e);
            throw e;
        }

        int expected = EmbeddingsConfig.expectedDimension;
        if (expected > 0 && vector.length != expected) {
            failures.incrementAndGet();
            // Loud on purpose. A wrong-width vector is worse than an error, because retrieval still
            // "works" — it just ranks in a space none of the tuning was measured in.
            IllegalStateException wrongWidth = new IllegalStateException(String.format(
                    "Embedding model '%s' at %s returned %d dimensions, expected %d. "
                            + "Every retrieval constant (int8 quantisation cost, candidate multiplier, k) "
                            + "was measured at %d. Point at the right model, or set "
                            + "embeddings.expectedDimension to 0 to accept this and re-measure.",
                    EmbeddingsConfig.model, EmbeddingsConfig.baseUrl,
                    vector.length, expected, expected));
            // Same latch as an unreachable endpoint, because the consequence is the same: pointed at
            // the wrong model, nothing can be stored or recalled at all.
            MemoryHealth.embedFailed(wrongWidth);
            throw wrongWidth;
        }

        observedDimension = vector.length;
        MemoryHealth.embedSucceeded();
        if (measured) {
            calls.incrementAndGet();
            totalMillis.addAndGet(elapsedMs);
            maxMillis.accumulateAndGet(elapsedMs, Math::max);
            minMillis.accumulateAndGet(elapsedMs, Math::min);
        }

        if (dimensionLogged.compareAndSet(false, true)) {
            LOGGER.info("Embeddings: {} at {} returned {} dimensions in {} ms ({}, "
                            + "includes any model load).",
                    EmbeddingsConfig.model, EmbeddingsConfig.baseUrl, vector.length, elapsedMs,
                    measured ? "first call" : "warm-up");
        }
        return vector;
    }

    /**
     * Pays the embedder's model-load cost once, off the critical path, so no player turn pays it.
     *
     * <h2>Why this exists</h2>
     *
     * Measured 2026-08-17 against {@code nomic-embed-text} on Ollama: the first call of a session took
     * <b>595 ms</b>, subsequent ones 0–42 ms. That is the model being paged in, not the embedding.
     *
     * <p>{@code CompanionMemory.recall} runs on the server thread with a {@code MemoryConfig.embedBudgetMs}
     * ceiling (250 ms), so whichever turn happens to be first in a session loses its recall to a
     * {@code TimeoutException} — and the companion then *denies knowing* something it has stored, which
     * is the worst failure shape available. Raising the budget past the load cost is not the fix: that
     * would put a ~700 ms stall on the server thread, which is the thing the budget exists to prevent.
     *
     * <p>It hid for a session because the ordering usually saves it — a {@code /companion rememberhere}
     * embeds on a pool thread with no budget, so a write happening to come first warms the model for
     * free. It only bites when the first embedding of a session is a recall.
     *
     * <p>Idempotent and non-blocking: safe to call from the server thread, and safe to call on every
     * {@code /companion reload}. Failure is ignored on purpose — if the embedder is down, the next real
     * turn will log that properly, and a warm-up has nothing to say that a recall will not say better.
     */
    public static void warmUp() {
        if (!EmbeddingsConfig.enabled || !warmed.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return embed(WARM_TEXT, false);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, pool())
                .whenComplete((vector, error) -> {
                    if (error != null) {
                        // Give the one attempt back. Observed 2026-08-17: the embedder was not running
                        // at world load, the warm-up failed, the owner pointed the config at a machine
                        // that WAS running it and reloaded — and no warm-up ever ran again, because the
                        // flag had already been spent on the attempt that could not have worked. A
                        // failure means the model was never loaded, so nothing has been warmed and
                        // there is nothing to protect; the next /companion reload should get another go.
                        warmed.set(false);
                        LOGGER.warn("Embeddings: warm-up failed ({}). Retrieval will still work, but "
                                + "the first recall pays the model load and may miss its {} ms budget. "
                                + "Fix the endpoint and /companion reload to try again.",
                                error.toString(), MemoryConfig.embedBudgetMs);
                    } else {
                        LOGGER.info("Embeddings: warm-up done — the model is loaded and the first "
                                + "recall will not pay for it.");
                    }
                });
    }

    /**
     * Embed off the calling thread.
     *
     * <p>The returned future completes exceptionally rather than throwing here, so a caller on the
     * server thread can attach a handler and carry on. A failure is not fatal to a conversation turn:
     * the companion answers without memory, which is how it behaves today anyway.
     */
    public static CompletableFuture<float[]> embedAsync(String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return embed(text);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, pool());
    }

    /**
     * Reads the vector out of the response.
     *
     * <p>Accepts the OpenAI shape ({@code data[0].embedding}) and Ollama's native shapes
     * ({@code embedding}, {@code embeddings[0]}), because the difference between them is one path
     * segment and mixing them up produces a confusing null rather than a clear error.
     */
    private static float[] extractVector(Map<String, JsonElement> response) {
        JsonElement data = response.get("data");
        if (data != null && data.isJsonArray() && !data.getAsJsonArray().isEmpty()) {
            JsonElement first = data.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                JsonElement embedding = first.getAsJsonObject().get("embedding");
                if (embedding != null && embedding.isJsonArray()) {
                    return toFloats(embedding.getAsJsonArray());
                }
            }
        }

        JsonElement embedding = response.get("embedding");
        if (embedding != null && embedding.isJsonArray()) {
            return toFloats(embedding.getAsJsonArray());
        }

        JsonElement embeddings = response.get("embeddings");
        if (embeddings != null && embeddings.isJsonArray() && !embeddings.getAsJsonArray().isEmpty()) {
            JsonElement first = embeddings.getAsJsonArray().get(0);
            if (first.isJsonArray()) {
                return toFloats(first.getAsJsonArray());
            }
        }

        throw new IllegalStateException(
                "No embedding in the response. Keys: " + response.keySet()
                        + ". Check that " + EmbeddingsConfig.baseUrl
                        + " serves an embedding model — a chat-only llama.cpp answers 501 here.");
    }

    private static float[] toFloats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static ExecutorService pool() {
        ExecutorService p = pool;
        if (p == null) {
            synchronized (EmbeddingsService.class) {
                p = pool;
                if (p == null) {
                    int threads = Math.max(1, Math.min(8, EmbeddingsConfig.maxConcurrentRequests));
                    ThreadFactory factory = r -> {
                        Thread t = new Thread(r, "aicompanion-embeddings");
                        t.setDaemon(true);
                        return t;
                    };
                    p = Executors.newFixedThreadPool(threads, factory);
                    pool = p;
                }
            }
        }
        return p;
    }

    /** Width the server actually returned, or 0 if nothing has succeeded yet. */
    public static int observedDimension() {
        return observedDimension;
    }

    /**
     * One-line latency summary for the log and {@code /companion} diagnostics, or a note that nothing
     * has been embedded yet.
     *
     * <p>Counts real embeddings only. {@link #warmUp()} is excluded, so these numbers describe what a
     * turn costs rather than what a cold model load costs — the two differ by an order of magnitude.
     */
    public static String stats() {
        long n = calls.get();
        if (n == 0) {
            return "embeddings: no successful calls yet (" + failures.get() + " failed)";
        }
        return String.format("embeddings: %d calls, %d dim, mean %d ms, min %d ms, max %d ms, %d failed",
                n, observedDimension, totalMillis.get() / n, minMillis.get(), maxMillis.get(),
                failures.get());
    }
}
