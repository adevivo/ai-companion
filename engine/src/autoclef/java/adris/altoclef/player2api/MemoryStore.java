package adris.altoclef.player2api;

import baritone.utils.DirUtil;
import com.neovetta.aicompanion.memory.MemoryCodec;
import com.neovetta.aicompanion.memory.MemoryRecord;
import com.neovetta.aicompanion.memory.MemoryScope;
import com.neovetta.aicompanion.memory.VectorIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * One player's memories on disk, and the write path that grows them.
 *
 * <h2>Where this lives, and why not in the world save</h2>
 *
 * Per player, <b>not</b> per world: {@code config/aicompanion/memories/&lt;playerUuid&gt;/}.
 *
 * <p>That looks wrong next to {@link WorldIdentity}, which deliberately lives in the save — but the
 * two want opposite things. A world id describes one save and must travel with it. A player's
 * memories include {@link MemoryScope#PERSON} facts that are true of them <em>everywhere</em>, and
 * storing those in a world would mean a preference learned in one save is unknown in the next.
 *
 * <p>World facts live here too, tagged with their world id, and are kept apart by
 * {@code RetrievalScope.admits()} — a hard filter applied before ranking, so a memory from another
 * save is unreachable rather than merely unlikely. That is also exactly the shape the hosted service
 * uses: one store per account, {@code world_id} as a column, the client filtering. <b>This file is
 * the offline cache that design already requires</b>, which is why building it now is not throwaway
 * work.
 *
 * <h2>Format</h2>
 *
 * The corpus format the engine already reads: {@code memories.jsonl} plus the packed sidecars
 * ({@code manifest.json}, {@code vectors.i8}, {@code vectors.f32}). Reusing it means the evaluation
 * harness can be pointed at a real player's corpus without a converter, and no second serialiser
 * exists to disagree with the first.
 *
 * <p><b>Not encrypted.</b> Phase 2 of the plan is deliberately plaintext-on-disk; encryption
 * arrives with the service, where the key management that makes it meaningful also lives. Anyone
 * with the config directory can read these.
 */
public final class MemoryStore {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Path dir;
    private final List<MemoryRecord> records;
    private final List<float[]> vectors;
    private volatile VectorIndex index;

    private MemoryStore(Path dir, List<MemoryRecord> records, List<float[]> vectors) {
        this.dir = dir;
        this.records = records;
        this.vectors = vectors;
        this.index = vectors.isEmpty() ? null : VectorIndex.of(vectors);
    }

    /** {@code config/aicompanion/memories/&lt;playerUuid&gt;/} */
    public static Path dirFor(UUID player) {
        return DirUtil.getConfigDir().resolve("aicompanion").resolve("memories")
                .resolve(player.toString());
    }

    /**
     * Loads a player's memories, or an empty store if they have none yet.
     *
     * <p>A corpus that fails to read is <b>not</b> silently discarded — it throws. Losing someone's
     * memories because one line was malformed is worse than refusing to start with them, and the
     * alternative would present as a companion that has quietly forgotten everything.
     */
    public static MemoryStore load(UUID player) throws IOException {
        Path dir = dirFor(player);
        Path jsonl = dir.resolve("memories.jsonl");
        if (!Files.exists(jsonl)) {
            return new MemoryStore(dir, new ArrayList<>(), new ArrayList<>());
        }

        List<MemoryRecord> records = MemoryCodec.readJsonl(jsonl);
        VectorIndex loaded = VectorIndex.load(dir);
        if (loaded.count() != records.size()) {
            throw new IOException("memories.jsonl has " + records.size() + " records but the vector "
                    + "sidecars hold " + loaded.count() + " — the corpus is torn, refusing to load "
                    + "rather than rank against vectors that describe other memories.");
        }

        // Re-materialise the per-row vectors so appends can rebuild the index without re-embedding.
        List<float[]> vectors = new ArrayList<>(records.size());
        for (int row = 0; row < loaded.count(); row++) {
            vectors.add(loaded.row(row));
        }
        LOGGER.info("Memory: loaded {} stored memories for {}", records.size(), player);
        return new MemoryStore(dir, records, vectors);
    }

    public List<MemoryRecord> records() {
        return List.copyOf(records);
    }

    public VectorIndex index() {
        return index;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * Adds a fact, or confirms one already held.
     *
     * <p>The incremental write path: look up the entity, compare the value, then confirm or
     * supersede. Batch resolution folds a whole corpus at once; a companion learns one thing at a
     * time and has to fold it into what it already believes.
     *
     * <ul>
     *   <li><b>Same entity, same value</b> — a confirmation, not a new memory. The existing record
     *       is reinforced, which raises its occurrence count without duplicating it.</li>
     *   <li><b>Same entity, different value</b> — the world changed. The old record is superseded
     *       (its valid time closes) and the new one is stored. Nothing is deleted; the old fact
     *       remains true of the past, which is what bitemporality is for.</li>
     *   <li><b>Unknown entity</b> — simply stored.</li>
     * </ul>
     *
     * <p>Entity matching is scoped: a world fact only contradicts a fact from the <em>same</em>
     * world. Without that, learning where the base is in one save would supersede where it is in
     * another.
     *
     * @return the record now held for this entity
     */
    public synchronized MemoryRecord remember(MemoryRecord incoming, float[] vector,
            Instant now) throws IOException {
        Optional<Integer> existing = indexOfEntity(incoming.entity(), incoming.scope(),
                incoming.worldId());

        if (existing.isPresent()) {
            int at = existing.get();
            MemoryRecord held = records.get(at);
            if (held.value() != null && held.value().equals(incoming.value())) {
                // Same claim — but a restatement can still carry something the held record lacks.
                boolean placeMoved = held.place() != null && incoming.place() != null
                        && !held.place().equals(incoming.place());
                if (placeMoved) {
                    // The claim is unchanged and its location is not: the thing moved. That is the
                    // world changing, which is a supersession, not a confirmation.
                    records.set(at, held.supersededAt(now));
                    LOGGER.info("Memory: \"{}\" moved from {} to {}",
                            held.value(), held.place(), incoming.place());
                } else {
                    MemoryRecord reinforced = held.reinforcedAt(now);
                    // A location learned later fills a gap rather than contradicting anything.
                    // Dropping it — which this did — loses the only part of the restatement worth
                    // having, and leaves the model to invent coordinates it was just handed.
                    if (held.place() == null && incoming.place() != null) {
                        reinforced = reinforced.withPlace(incoming.place());
                        LOGGER.info("Memory: confirmed \"{}\" and learned where: {}",
                                reinforced.value(), incoming.place());
                    } else {
                        LOGGER.info("Memory: confirmed \"{}\" (seen {} times)",
                                reinforced.value(), reinforced.occurrences());
                    }
                    records.set(at, reinforced);
                    persist();
                    return reinforced;
                }
            } else {
                records.set(at, held.supersededAt(now));
                LOGGER.info("Memory: superseded \"{}\" with \"{}\"", held.value(),
                        incoming.value());
            }
        }

        MemoryRecord stored = incoming.withVectorRow(vectors.size());
        records.add(stored);
        vectors.add(vector);
        index = VectorIndex.of(vectors);
        persist();
        // Additions were the one path that logged nothing, which made "did that write land?" a
        // question only answerable by going and reading the files.
        LOGGER.info("Memory: learned \"{}\" [{}{}{}] — {} now stored",
                stored.value(), stored.scope(),
                stored.worldId() == null ? "" : " " + stored.worldId(),
                stored.place() == null ? "" : " @ " + stored.place(),
                records.size());
        return stored;
    }

    /**
     * The believed record for an entity within one scope, if any.
     *
     * <p>Superseded and retracted records are skipped: they are history, and contradicting history
     * is not a contradiction.
     */
    private Optional<Integer> indexOfEntity(String entity, MemoryScope scope, String worldId) {
        for (int i = 0; i < records.size(); i++) {
            MemoryRecord r = records.get(i);
            if (!r.isCurrent() || !r.entity().equals(entity) || r.scope() != scope) {
                continue;
            }
            boolean sameWorld = worldId == null
                    ? r.worldId() == null
                    : worldId.equals(r.worldId());
            if (sameWorld) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /**
     * Writes records and vectors together.
     *
     * <p>⚠️ Not atomic, and the two files can disagree if the process dies between them. That is
     * caught on the next load by the count check rather than being read as valid — the corpus
     * refuses to open instead of ranking against vectors that describe other memories.
     */
    private void persist() throws IOException {
        Files.createDirectories(dir);
        MemoryCodec.writeJsonl(dir.resolve("memories.jsonl"), records);
        if (index != null) {
            index.save(dir);
        }
    }
}
