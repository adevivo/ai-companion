package adris.altoclef.player2api;

import com.neovetta.aicompanion.memory.ImportanceSource;
import com.neovetta.aicompanion.memory.MemoryContext;
import com.neovetta.aicompanion.memory.MemoryFrame;
import com.neovetta.aicompanion.memory.MemoryKind;
import com.neovetta.aicompanion.memory.MemoryRecord;
import com.neovetta.aicompanion.memory.MemoryRetriever;
import com.neovetta.aicompanion.memory.MemoryScope;
import com.neovetta.aicompanion.memory.MemoryScorer;
import com.neovetta.aicompanion.memory.Partition;
import com.neovetta.aicompanion.memory.Provenance;
import com.neovetta.aicompanion.memory.RetrievalScope;
import com.neovetta.aicompanion.memory.ScoredMemory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The companion's long-term memory: what it recalls, and how it learns.
 *
 * <p>Memories live in {@link MemoryStore}, one store per player, and survive a restart. Retrieval is
 * automatic and invisible — the turn is embedded, the store is ranked, and the best matches are put
 * into the prompt before the model runs. Nothing depends on the model choosing to ask for a memory,
 * which is what makes this work on small local models.
 *
 * <h2>Threading</h2>
 *
 * {@link #recall} has two callers with opposite constraints, and the budget is what separates them.
 *
 * <p>On the <b>server thread</b> it normally makes no network call at all: {@link #prefetch} starts
 * the embedding when the message is queued, at least a tick earlier, and recall collects the finished
 * vector. The bounded wait is a fallback, not the path, and it is tight (250 ms) because a longer one
 * is a hitch every player in the world can feel.
 *
 * <p>On a <b>client</b> doing its own thinking, none of that holds: the work runs on a pool thread
 * with no tick to miss, and prefetch is deliberately skipped. That caller takes the {@code budgetMs}
 * overload and passes the embedder's own timeout. Handing it the tick-loop budget instead is not
 * caution, it is silent data loss — see that overload for the measurement.
 *
 * <p>{@link #remember} embeds and writes files, so it must not be called on the server thread.
 */
public final class CompanionMemory {
    private static final Logger LOGGER = LogManager.getLogger();

    private CompanionMemory() {}

    /**
     * Demonstration facts, written once into an empty store when {@code memory.seedDemoFacts} is on.
     *
     * <p><b>Fiction, and off by default.</b> They existed so retrieval could be judged before there
     * was anything real to recall. Once a player has memories of their own these are noise at best
     * and misleading at worst — a companion confidently discussing a wolf that does not exist.
     */
    private static final String[] DEMO_PERSON_FACTS = {
        "The player prefers cobblestone over wood for anything left out in the weather.",
        "The player thinks dirt huts look lazy and tears them down on sight.",
        "The player is afraid of caves and would rather strip-mine than explore one.",
        "The player always plants saplings back after chopping a tree down.",
    };

    private static final String[] DEMO_WORLD_FACTS = {
        "The player is building a bridge across the ravine east of the base.",
        "The player's base is in a taiga, close to a village with a library.",
        "The player keeps a wolf named Biscuit and will not let it near creepers.",
        "The player lost a full set of diamond gear to lava under the desert temple.",
    };

    /** One store per player, loaded on first use and kept for the session. */
    private static final Map<UUID, MemoryStore> stores = new ConcurrentHashMap<>();

    /** Derived from the stores, never authoritative — rebuilt after every write. */
    private static final Map<UUID, MemoryRetriever> retrievers = new ConcurrentHashMap<>();

    /** Per-player subject tokens for the gate, derived from the store. See {@link #rebuild}. */
    private static final Map<UUID, Set<String>> subjects = new ConcurrentHashMap<>();

    /** Provenance for a fact the player typed into a command: authored by them, true by construction. */
    public static final String SOURCE_COMMAND = "remember";

    /** Provenance for a fact {@link MemoryExtractor} read out of a conversation. */
    public static final String SOURCE_EXTRACTOR = "extractor";

    private static final AtomicLong recalls = new AtomicLong();
    private static final AtomicLong recallMillis = new AtomicLong();
    private static final AtomicLong misses = new AtomicLong();
    private static final AtomicLong gated = new AtomicLong();

    /**
     * Query embeddings started at queue time, keyed by the exact turn text.
     *
     * <p>Keyed by text rather than by conversation on purpose: two companions hearing the same
     * broadcast line share one embedding instead of making the same call twice.
     */
    private static final ConcurrentHashMap<String, CompletableFuture<float[]>> pending =
            new ConcurrentHashMap<>();

    private static final int PENDING_CAP = 64;

    /**
     * Loads memory for everyone currently connected. Idempotent — a loaded store is left alone.
     *
     * <p>Called when a companion's brain comes up and on {@code /companion reload}, so a player who
     * never spawns a companion never touches the embedder or the disk.
     */
    public static void warm(net.minecraft.server.MinecraftServer server) {
        warm(server == null ? null : server.overworld());
    }

    /** As above, for a caller that already has the level. */
    public static void warm(ServerLevel level) {
        if (!MemoryConfig.enabled || level == null) {
            return;
        }
        if (!EmbeddingsConfig.enabled) {
            LOGGER.warn("Memory is enabled but embeddings are not — nothing can be ranked. "
                    + "Set embeddings.enabled=true.");
            // The player switched memory on and it will do nothing at all. That is worth a line in
            // their chat rather than only in a log they are not reading while playing.
            MemoryHealth.embeddingsOff();
            return;
        }
        MemoryHealth.embeddingsOn();
        // When the owning client does the thinking, it also owns the corpus — so loading every
        // player's memories here is work for nothing, and worse than nothing. The server's copy
        // stops being written the moment the switch is flipped, so it freezes at that instant while
        // the client's moves on; turning clientBrain back off would then answer from a stale
        // snapshot, which reads as "it forgot the last month" rather than "it is reading the wrong
        // file". Not loading it makes the fallback memory-less, which is correct: those memories
        // belong to the client.
        //
        // Players whose client CANNOT think still get a server-side store — loaded on demand by
        // LocalBrainTransport when it actually has to answer for them, rather than speculatively
        // for everyone here.
        if (LlmConfig.clientBrain && LlmConfig.localMode) {
            LOGGER.info("Memory: not loading corpora — llm.clientBrain is on, so each player's "
                    + "memories live on their own client. A player whose client cannot think gets "
                    + "a server-side store loaded on demand.");
            return;
        }
        // Before any store is touched: the model load is process-global and costs ~600 ms, which is
        // more than a recall's whole budget. Paying it here means no player turn pays it. Idempotent,
        // and outside the per-player loop below because a returning player whose store is already
        // loaded still needs a warm embedder. See EmbeddingsService.warmUp().
        EmbeddingsService.warmUp();
        // What memory is actually set to do, once per warm — which is world load and every
        // /companion reload. Cheap, and it answers from the log alone the question that otherwise
        // needs someone to go and read a config file on another machine: is extraction even on
        // here? A player's config pins its own values, so a changed default never reaches them and
        // the setting cannot be inferred from the version.
        LOGGER.info("Memory: extraction {}, gate {}, k={}, minCosine={}, margin={}, embed budget {} ms",
                MemoryConfig.extractionEnabled
                        ? "ON"
                        : "OFF — nothing will be learned from conversation "
                                + "(set memory.extractionEnabled=true)",
                MemoryConfig.gateEnabled ? "on" : "off",
                MemoryConfig.topK, MemoryConfig.minCosine, MemoryConfig.relativeMargin,
                MemoryConfig.embedBudgetMs);
        // Resolved on the server thread: WorldIdentity touches SavedData, which the async work
        // below must not.
        WorldIdentity identity = WorldIdentity.of(level);
        String worldId = identity.id();
        String worldLabel = identity.label();

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (stores.containsKey(id)) {
                continue;
            }
            CompletableFuture.runAsync(() -> loadFor(id, worldId, worldLabel));
        }
    }

    /**
     * Load one player's corpus with no level to hand — the client-side entry point.
     *
     * <p>{@link #warm(ServerLevel)} cannot be reused: it walks the player list and resolves a world
     * id through {@code SavedData}, neither of which exists on a client. A client knows exactly one
     * player — the one at the keyboard — and is told the world id by the server.
     *
     * <p>Idempotent, and safe to call on every turn: a loaded store is left alone.
     */
    public static void warmForClient(UUID player) {
        if (!MemoryConfig.enabled || player == null || stores.containsKey(player)) {
            return;
        }
        if (!EmbeddingsConfig.enabled) {
            MemoryHealth.embeddingsOff();
            return;
        }
        MemoryHealth.embeddingsOn();
        EmbeddingsService.warmUp();
        loadFor(player, null, "client");
    }

    private static void loadFor(UUID player, String worldId, String worldLabel) {
        try {
            MemoryStore store = MemoryStore.load(player);
            stores.put(player, store);

            if (store.isEmpty() && MemoryConfig.seedDemoFacts) {
                LOGGER.info("Memory: store empty and seedDemoFacts is on — writing {} FICTIONAL "
                                + "demo facts. These are not real memories of this player.",
                        DEMO_PERSON_FACTS.length + DEMO_WORLD_FACTS.length);
                for (String fact : DEMO_PERSON_FACTS) {
                    writeFact(player, fact, MemoryScope.PERSON, null);
                }
                for (String fact : DEMO_WORLD_FACTS) {
                    writeFact(player, fact, MemoryScope.WORLD, worldId);
                }
            }
            rebuild(player);
            MemoryHealth.storeLoaded();
            LOGGER.info("Memory: ready for {} — {} stored. World \"{}\" = {}",
                    player, store.records().size(), worldLabel, worldId);
        } catch (Throwable e) {
            // Throwable, not Exception: a version skew between the engine and the memory jar
            // arrives as an Error, and catching only Exception once let one die silently inside an
            // unobserved CompletableFuture. The symptom was a companion that never recalled
            // anything, with nothing in the log at all.
            LOGGER.warn("Memory: could not load the store for {}. The companion will run without "
                    + "memories; everything else is unaffected.", player, e);
            // Distinct from every other failure here: the memories still exist. MemoryStore.load
            // throws rather than discarding a torn corpus, so the file is intact and the player
            // should be told to look at it, not told their companion has forgotten them.
            MemoryHealth.storeUnreadable(e);
        }
    }

    /**
     * Load one player's corpus because the server has to answer for them after all.
     *
     * <p>For the fallback path when {@code llm.clientBrain} is on: the eager warm was skipped, but
     * this player's client cannot think, so the server needs their memories. Asynchronous and
     * idempotent — the turn that triggers it recalls nothing and the next one works, which is the
     * same shape as any cold start and far better than a file read on the server thread.
     */
    public static void loadOnDemand(UUID player, String worldId) {
        if (!MemoryConfig.enabled || !EmbeddingsConfig.enabled || player == null
                || stores.containsKey(player)) {
            return;
        }
        EmbeddingsService.warmUp();
        CompletableFuture.runAsync(() -> loadFor(player, worldId, "on demand"));
    }

    /** Rebuilds a player's retriever from their store. Called after every write. */
    private static void rebuild(UUID player) {
        MemoryStore store = stores.get(player);
        if (store == null || store.isEmpty()) {
            retrievers.remove(player);
            subjects.remove(player);
            return;
        }
        retrievers.put(player, new MemoryRetriever(
                store.records(), store.index(), MemoryScorer.measured()));

        // Indexed here rather than per turn: recall() runs on the server thread, and the token set is
        // a function of the store, which only changes on a write. Every record counts, including ones
        // no longer valid — a superseded "home" still makes the WORD "home" worth admitting, and the
        // scorer decides which record answers. Gating on validity here would make asking about a fact
        // that has moved fail at the gate, silently, which is the exact failure being fixed.
        Set<String> tokens = new HashSet<>();
        for (MemoryRecord record : store.records()) {
            tokens.addAll(MemoryGate.tokensOf(record.text()));
        }
        subjects.put(player, Set.copyOf(tokens));
    }

    /** What each player's stored memories are ABOUT, for {@link MemoryGate}. Rebuilt on every write. */
    private static Set<String> subjectsOf(UUID player) {
        Set<String> tokens = subjects.get(player);
        return tokens == null ? Set.of() : tokens;
    }

    /**
     * Teaches the companion something and persists it.
     *
     * <p>⚠️ Blocking — it embeds and writes files, so it must not run on the server thread.
     *
     * @param scope whether this is true of the player everywhere, or only in this world
     * @param worldId required when {@code scope} is {@link MemoryScope#WORLD}
     * @return the record now held for this fact
     */
    public static MemoryRecord remember(UUID player, String text, MemoryScope scope, String worldId)
            throws Exception {
        return remember(player, text, scope, worldId, null);
    }

    /**
     * As above, recording where it happened.
     *
     * <p>A memory about a place needs the place in it. Without one the model has a sentence and no
     * coordinates, and when asked "where is home?" it fills the gap from whatever else is in the
     * packet — observed live, it answered with the world's spawn point taken from {@code
     * worldStatus} and presented it as something it remembered. That is worse than inventing a
     * number, because the number is real and merely belongs to something else.
     */
    public static MemoryRecord remember(UUID player, String text, MemoryScope scope, String worldId,
            com.neovetta.aicompanion.memory.Place place) throws Exception {
        return remember(player, text, scope, worldId, place, SOURCE_COMMAND);
    }

    /**
     * As above, recording HOW the companion came to know it.
     *
     * <p>{@code sourceId} lands in {@link Provenance} and is the only thing that will distinguish a
     * fact the player typed from one the extractor inferred, once both are in the same store. That
     * distinction is not cosmetic: a commanded memory was authored by the player and is true by
     * construction, while an extracted one is a model's reading of a conversation and carries the
     * measured error rates that come with it. Anything that later audits, weights or retracts memories
     * needs to be able to tell them apart, and provenance is the only place that survives a restart.
     */
    public static MemoryRecord remember(UUID player, String text, MemoryScope scope, String worldId,
            com.neovetta.aicompanion.memory.Place place, String sourceId) throws Exception {
        if (!MemoryConfig.enabled) {
            throw new IllegalStateException("Memory is disabled.");
        }
        if (!EmbeddingsConfig.enabled) {
            throw new IllegalStateException("Embeddings are disabled, so nothing could rank this.");
        }
        if (!stores.containsKey(player)) {
            stores.put(player, MemoryStore.load(player));
        }
        MemoryRecord stored = writeFact(player, text, scope, worldId, place, sourceId);
        rebuild(player);
        return stored;
    }

    /** Embeds one fact and folds it into a player's store. */
    private static MemoryRecord writeFact(UUID player, String text, MemoryScope scope,
            String worldId) {
        return writeFact(player, text, scope, worldId, null, SOURCE_COMMAND);
    }

    private static MemoryRecord writeFact(UUID player, String text, MemoryScope scope,
            String worldId, com.neovetta.aicompanion.memory.Place place) {
        return writeFact(player, text, scope, worldId, place, SOURCE_COMMAND);
    }

    private static MemoryRecord writeFact(UUID player, String text, MemoryScope scope,
            String worldId, com.neovetta.aicompanion.memory.Place place, String sourceId) {
        try {
            MemoryStore store = stores.get(player);
            Instant now = Instant.now();
            float[] vector = EmbeddingsService.embed(text);

            MemoryRecord.Builder b = MemoryRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .serial(store.records().size())
                    .text(text)
                    .entity(entityOf(text))
                    .value(text)
                    .kind(MemoryKind.FACT)
                    .validFrom(now)
                    .recordedAt(now)
                    .lastSeenAt(now)
                    .occurrences(1)
                    .topicDays(1)
                    // Flat: importance carries no measurable information and ships at weight zero.
                    .importance(0.5f)
                    .importanceSource(ImportanceSource.UNRATED)
                    .context(MemoryContext.GAME)
                    .partition(Partition.OWNER)
                    .frame(MemoryFrame.REAL)
                    .provenance(new Provenance("in-game", sourceId, player.toString(),
                            now.toEpochMilli()));
            if (scope == MemoryScope.WORLD) {
                b.inWorld(worldId);
                if (place != null) {
                    b.place(place);
                }
            } else {
                b.aboutPerson();
            }
            return store.remember(b.build(), vector, now);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The entity a fact is about, derived from its text.
     *
     * <p>⚠️ <b>The weakest part of the write path, and knowingly a placeholder.</b> Contradiction
     * resolution works by entity: "the base is in a taiga" should supersede "the base is in a
     * desert", because both are about <em>the base</em>. Deriving an entity from normalised text
     * cannot do that — the sentences differ, so they become two entities and both stay believed.
     *
     * <p>What it does buy is real: saying the same thing twice confirms rather than duplicates.
     * Proper resolution needs extraction to emit a subject and a predicate separately, which is the
     * next piece of work — so this deliberately does not pretend to be more than it is.
     */
    private static String entityOf(String text) {
        return "text:" + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /**
     * Starts embedding a turn now, so the vector is ready when the turn dispatches.
     *
     * <p>Called when a player message is queued — at least a tick before the conversation manager
     * picks it up, which is what keeps a network call off the tick loop.
     */
    public static void prefetch(String turnText, UUID player) {
        if (!MemoryConfig.enabled || turnText == null || turnText.isBlank()) {
            return;
        }
        // Same tokens recall() will use. A prefetch that gated differently from the recall it feeds
        // would quietly cost the recall its head start — the turn would be admitted later, with no
        // vector waiting, and pay the full embed inside embedBudgetMs.
        if (!MemoryGate.admits(turnText, subjectsOf(player))) {
            return;
        }
        pending.computeIfAbsent(turnText, EmbeddingsService::embedAsync);
        if (pending.size() > PENDING_CAP) {
            pending.clear();
        }
    }

    /**
     * The memories worth showing this turn, best first, or an empty list.
     *
     * <p>Never throws. Every failure degrades to "no memories", which is exactly how the companion
     * behaves with the feature switched off.
     *
     * <p>Uses {@link MemoryConfig#embedBudgetMs}, which is the <b>server tick loop's</b> budget. A
     * caller that is not on the tick loop wants {@link #recall(String, String, UUID, String, int)}.
     */
    public static List<String> recall(String turnText, String companionId, UUID player,
            String worldId) {
        return recall(turnText, companionId, player, worldId, MemoryConfig.embedBudgetMs);
    }

    /**
     * As above, with an explicit ceiling on how long the query embedding may take.
     *
     * <p><b>The budget is a property of the caller, not of memory.</b> {@code embedBudgetMs} is 250 ms
     * because {@code AgentConversationData.process()} runs on the server thread, where a longer wait
     * is a visible hitch for every player in the world. Nothing else about recall wants a ceiling
     * that tight.
     *
     * <p>⚠️ Applying it off the tick loop is pure loss, and was measured as such. On 2026-08-20 a
     * client-side session lost 2 of the 7 recalls that reached the embedder to
     * {@code TimeoutException after 255 ms (budget 250 ms)} — both on a cold embedder, one on the
     * session's first turn and one after a four-minute idle gap. Warm recalls in the same session
     * took 45–56 ms. The cost is worse than the count suggests: the turns lost are the first question
     * after a pause, which is exactly when a player is asking whether it remembers.
     *
     * <p>It compounds with prefetch. {@code NetworkBrainTransport} deliberately does not prefetch
     * when the owning client is thinking — correctly, since a client is not racing a tick — so every
     * client-side recall embeds cold inside a budget whose whole design assumed prefetch had already
     * paid that cost.
     *
     * @param budgetMs ceiling on the embedding wait. A client passes
     *                 {@link EmbeddingsConfig#timeoutMs}, which is the bound that actually matters
     *                 there: the embedder being wedged, rather than a tick being late.
     */
    public static List<String> recall(String turnText, String companionId, UUID player,
            String worldId, int budgetMs) {
        if (!MemoryConfig.enabled || player == null || turnText == null || turnText.isBlank()) {
            return List.of();
        }
        // Gate first, store second. The other order returned silently whenever the store was empty,
        // which produced NO log line at all — indistinguishable from recall never having run, and
        // observed doing exactly that on 2026-08-18 when the player asked "do you recall my dog's
        // name?" against an empty store. Both branches still return an empty list, so nothing the
        // companion does changes; what changes is that a turn now always leaves a verdict behind.
        // It also makes `gated` mean what its name says — turns the gate skipped — rather than
        // "turns the gate skipped, among those that happened to have something stored".
        Set<String> storedSubjects = subjectsOf(player);
        if (!MemoryGate.admits(turnText, storedSubjects)) {
            gated.incrementAndGet();
            LOGGER.info("Memory: no recall — gate skipped this turn ({})",
                    MemoryGate.explain(turnText, storedSubjects));
            return List.of();
        }

        MemoryRetriever r = retrievers.get(player);
        if (r == null) {
            LOGGER.info("Memory: no recall — this turn wanted a memory, but nothing is stored for "
                    + "{} yet.", player);
            return List.of();
        }

        long startedAt = System.nanoTime();
        try {
            CompletableFuture<float[]> future = pending.remove(turnText);
            if (future == null) {
                future = EmbeddingsService.embedAsync(turnText);
            }
            float[] query = future.get(budgetMs, TimeUnit.MILLISECONDS);

            // Bound to the save: a world memory from another world is unreachable here rather than
            // merely ranked low. See RetrievalScope.admits().
            RetrievalScope scope = companionId == null || companionId.isBlank()
                    ? RetrievalScope.everything().inWorld(worldId)
                    : RetrievalScope.forCompanionInWorld(companionId, worldId);

            List<ScoredMemory> hits = r.retrieve(query, Instant.now(), MemoryConfig.topK, scope);

            // Two filters answering two questions: the floor asks whether a memory is relevant at
            // all, the margin asks whether it is as good as the best one or merely next in a list
            // that had to return something.
            double best = hits.isEmpty() ? 0 : hits.get(0).cosine();
            List<String> out = new ArrayList<>(hits.size());
            for (ScoredMemory hit : hits) {
                if (hit.cosine() < MemoryConfig.minCosine) {
                    continue;
                }
                if (MemoryConfig.relativeMargin > 0
                        && hit.cosine() < best - MemoryConfig.relativeMargin) {
                    continue;
                }
                // Carry the coordinates into the prompt. A located memory whose text alone
                // reaches the model is precisely how it ends up borrowing a number from elsewhere
                // in the packet and calling it a memory.
                MemoryRecord mem = hit.memory();
                out.add(mem.place() == null
                        ? mem.text()
                        : mem.text() + " (" + mem.place() + ")");
            }

            long ms = (System.nanoTime() - startedAt) / 1_000_000L;
            recalls.incrementAndGet();
            recallMillis.addAndGet(ms);
            // The machinery ran, which is what health is about. Whether it FOUND anything is a
            // separate question with a perfectly good "no" — see MemoryHealth.recallSucceeded().
            MemoryHealth.recallSucceeded();
            if (out.isEmpty()) {
                LOGGER.info("Memory: no recall — {} candidates, best cosine {} below floor {} "
                                + "(took {} ms)",
                        hits.size(), hits.isEmpty() ? "n/a" : String.format("%.3f", best),
                        MemoryConfig.minCosine, ms);
            } else {
                LOGGER.info("Memory: recalled {} of {} candidates, best cosine {}, in {} ms",
                        out.size(), hits.size(), String.format("%.3f", best), ms);
            }
            return out;
        } catch (Exception e) {
            // TimeoutException is an expected failure rather than an exceptional one, but it must
            // still be visible: a turn that quietly skipped its embedding looks identical to a turn
            // where nothing was relevant, and those want opposite fixes.
            misses.incrementAndGet();
            // Only a timeout is counted towards "memory is too slow". Anything else means the embed
            // itself failed, and EmbeddingsService has already reported that with the real reason —
            // counting it here too would announce the same outage twice in two different words.
            if (e instanceof java.util.concurrent.TimeoutException) {
                MemoryHealth.recallTimedOut();
            }
            LOGGER.info("Memory: no recall — {} after {} ms (budget {} ms)",
                    e.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000L, budgetMs);
            return List.of();
        }
    }

    /** How many memories a player currently holds. */
    public static int countFor(UUID player) {
        MemoryStore store = stores.get(player);
        return store == null ? 0 : store.records().size();
    }

    /** One line for the log and {@code /companion stats}. */
    public static String stats(UUID player) {
        if (!MemoryConfig.enabled) {
            return "memory: disabled";
        }
        MemoryStore store = player == null ? null : stores.get(player);
        if (store == null) {
            return "memory: no store loaded yet";
        }
        long n = recalls.get();
        String timing = n == 0 ? "no recalls yet"
                : String.format("%d recalls, mean %d ms", n, recallMillis.get() / n);
        return String.format("memory: %d stored, k=%d, %s, %d gated, %d missed, %s",
                store.records().size(), MemoryConfig.topK, timing, gated.get(), misses.get(),
                MemoryHealth.summary());
    }
}
