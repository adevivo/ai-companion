package adris.altoclef.player2api;

import com.neovetta.aicompanion.memory.ImportanceSource;
import com.neovetta.aicompanion.memory.MemoryContext;
import com.neovetta.aicompanion.memory.MemoryFrame;
import com.neovetta.aicompanion.memory.MemoryKind;
import com.neovetta.aicompanion.memory.MemoryRecord;
import com.neovetta.aicompanion.memory.MemoryRetriever;
import com.neovetta.aicompanion.memory.MemoryScorer;
import com.neovetta.aicompanion.memory.Partition;
import com.neovetta.aicompanion.memory.Provenance;
import com.neovetta.aicompanion.memory.RetrievalScope;
import com.neovetta.aicompanion.memory.ScoredMemory;
import com.neovetta.aicompanion.memory.VectorIndex;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The retrieval slice: hard-coded memories, embedded once, ranked against each turn.
 *
 * <p><b>This is a probe, not a feature.</b> It exists to answer whether recalling the right fact
 * improves the companion, before storage, encryption, a write path or entity resolution are built
 * for it. Consequently:
 *
 * <ul>
 *   <li>The memories are <b>the same fictional set for every player</b> and are listed below.</li>
 *   <li>Nothing persists. The index is rebuilt on every server start.</li>
 *   <li>Nothing is ever written. The companion cannot learn a new fact; it can only recall these.</li>
 * </ul>
 *
 * <p>What it does exercise is real: the embedder, the scoring engine as it actually ships, the
 * prompt seam, and the latency all of that costs a turn.
 *
 * <h2>Threading</h2>
 *
 * The index is built <b>asynchronously</b> — embedding the seed facts is one network call each, and
 * doing that on the server thread would stall world load. Until it is ready, {@link #recall} returns
 * nothing, so the companion simply behaves as it does today.
 *
 * <p>{@link #recall} itself is called on the <b>server thread</b> and waits at most
 * {@link MemoryConfig#embedBudgetMs} for the turn's embedding. See that field for why this is a
 * known flaw rather than an oversight.
 */
public final class CompanionMemory {
    private static final Logger LOGGER = LogManager.getLogger();

    private CompanionMemory() {}

    /**
     * The seed facts.
     *
     * <p>Chosen to make retrieval observable rather than to flatter it. There are near-neighbours
     * ("cobblestone for exposed builds" vs "dislikes dirt huts") so ranking has to actually
     * discriminate, and several facts that no plausible chat turn will match, so a turn about
     * nothing should return nothing once {@link MemoryConfig#minCosine} is applied.
     */
    private static final String[] SEED_FACTS = {
        "The player is building a bridge across the ravine east of the base.",
        "The player prefers cobblestone over wood for anything left out in the weather.",
        "The player thinks dirt huts look lazy and tears them down on sight.",
        "The player's base is in a taiga, close to a village with a library.",
        "The player is afraid of caves and would rather strip-mine than explore one.",
        "The player keeps a wolf named Biscuit and will not let it near creepers.",
        "The player lost a full set of diamond gear to lava under the desert temple.",
        "The player always plants saplings back after chopping a tree down.",
    };

    /** Fixed, so the same fact keeps the same identity across restarts even without storage. */
    private static final Instant SEEDED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private static final AtomicBoolean started = new AtomicBoolean();
    private static volatile MemoryRetriever retriever;
    private static volatile String unavailableReason;

    // Latency accounting, so "what did retrieval cost the turn" is answerable from the log.
    private static final AtomicLong recalls = new AtomicLong();
    private static final AtomicLong recallMillis = new AtomicLong();
    private static final AtomicLong misses = new AtomicLong();

    /**
     * Builds the index in the background, once. Safe to call repeatedly; only the first starts work.
     *
     * <p>Called when a companion's brain comes up rather than at mod init, so a player who never
     * spawns a companion never touches the embedder.
     */
    public static void warm() {
        if (!MemoryConfig.enabled) {
            return;
        }
        if (!EmbeddingsConfig.enabled) {
            // Deliberately a warning: memory on with embeddings off is a config mistake that would
            // otherwise present as "memory silently does nothing".
            LOGGER.warn("Memory is enabled but embeddings are not — nothing can be ranked. "
                    + "Set embeddings.enabled=true.");
            unavailableReason = "embeddings disabled";
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
            try {
                List<MemoryRecord> records = new ArrayList<>(SEED_FACTS.length);
                List<float[]> vectors = new ArrayList<>(SEED_FACTS.length);

                for (int i = 0; i < SEED_FACTS.length; i++) {
                    String fact = SEED_FACTS[i];
                    // Sequential on purpose. These are the only embeddings in flight, and on a
                    // single-GPU host overlapping them with the brain is what crashes the machine.
                    vectors.add(EmbeddingsService.embed(fact));
                    records.add(seedRecord(i, fact, vectors.size() - 1));
                }

                MemoryRetriever built = new MemoryRetriever(
                        records, VectorIndex.of(vectors), MemoryScorer.measured());
                retriever = built;
                unavailableReason = null;
                LOGGER.info("Memory: indexed {} seed facts at {} dimensions in {} ms.",
                        records.size(), built.vectors().dim(),
                        (System.nanoTime() - startedAt) / 1_000_000L);
            } catch (Exception e) {
                unavailableReason = e.getMessage();
                // Release the latch so a later warm() can try again. Without this a single failure
                // — an embedder that had not finished starting, most likely — would be permanent
                // for the whole server session, with /companion reload unable to clear it.
                started.set(false);
                LOGGER.warn("Memory: could not build the index ({}). The companion will run without"
                        + " memories; everything else is unaffected. Fix the embedder and run"
                        + " /companion reload to retry.", e.getMessage());
            }
        });
    }

    /**
     * The memories worth showing this turn, best first, or an empty list.
     *
     * <p>Never throws. Every failure — index not ready, embedder down, nothing relevant enough —
     * degrades to "no memories", which is exactly how the companion behaves today.
     *
     * @param turnText what the player actually said
     * @param companionId the speaking companion, for scoping; may be null
     */
    public static List<String> recall(String turnText, String companionId) {
        MemoryRetriever r = retriever;
        if (!MemoryConfig.enabled || r == null || turnText == null || turnText.isBlank()) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        try {
            // Bounded wait: a slow or absent embedder must cost the turn a few ms, never seconds.
            float[] query = EmbeddingsService.embedAsync(turnText)
                    .get(MemoryConfig.embedBudgetMs, TimeUnit.MILLISECONDS);

            RetrievalScope scope = companionId == null || companionId.isBlank()
                    ? RetrievalScope.everything()
                    : RetrievalScope.forCompanion(companionId);

            List<ScoredMemory> hits = r.retrieve(query, Instant.now(), MemoryConfig.topK, scope);

            List<String> out = new ArrayList<>(hits.size());
            for (ScoredMemory hit : hits) {
                // Relevance floor. Top-k alone always returns k, so without this every "hello"
                // drags in the least-irrelevant fact and the companion free-associates.
                if (hit.cosine() >= MemoryConfig.minCosine) {
                    out.add(hit.memory().text());
                }
            }

            long ms = (System.nanoTime() - startedAt) / 1_000_000L;
            recalls.incrementAndGet();
            recallMillis.addAndGet(ms);
            LOGGER.debug("Memory: {} of {} candidates cleared {} in {} ms",
                    out.size(), hits.size(), MemoryConfig.minCosine, ms);
            return out;
        } catch (Exception e) {
            // Includes TimeoutException, which is the expected failure, not an exceptional one.
            misses.incrementAndGet();
            LOGGER.debug("Memory: no recall this turn ({})", e.getClass().getSimpleName());
            return List.of();
        }
    }

    /** One line for the log and {@code /companion stats}. */
    public static String stats() {
        if (!MemoryConfig.enabled) {
            return "memory: disabled";
        }
        MemoryRetriever r = retriever;
        if (r == null) {
            return "memory: not ready"
                    + (unavailableReason == null ? " (building)" : " — " + unavailableReason);
        }
        long n = recalls.get();
        String timing = n == 0 ? "no recalls yet"
                : String.format("%d recalls, mean %d ms", n, recallMillis.get() / n);
        return String.format("memory: %d facts, k=%d, %s, %d missed",
                r.records().size(), MemoryConfig.topK, timing, misses.get());
    }

    /**
     * A seed fact as a believed, current, companion-agnostic memory about the player.
     *
     * <p>{@code companionId} is null and the frame is {@link MemoryFrame#REAL}: these are facts about
     * the player, so they belong to the player rather than to whoever happens to be speaking, and
     * every companion should recall them. An {@code IN_WORLD} memory would be gated to one companion
     * and is the wrong shape for this.
     */
    private static MemoryRecord seedRecord(int i, String fact, int vectorRow) {
        return MemoryRecord.builder()
                .id("seed-" + i)
                .serial(i)
                .text(fact)
                .entity("seed:" + i)
                .value(fact)
                .kind(MemoryKind.FACT)
                .validFrom(SEEDED_AT)
                .recordedAt(SEEDED_AT)
                .lastSeenAt(SEEDED_AT)
                .occurrences(1)
                .topicDays(1)
                // Flat, and it does not matter: importance carries no measurable information and
                // ships at weight zero. Kept uniform so nothing here implies otherwise.
                .importance(0.5f)
                .importanceSource(ImportanceSource.UNRATED)
                .context(MemoryContext.CHAT)
                .partition(Partition.OWNER)
                .frame(MemoryFrame.REAL)
                .provenance(new Provenance("seed", "slice", "turn-" + i, SEEDED_AT.toEpochMilli()))
                .vectorRow(vectorRow)
                .build();
    }
}
