package adris.altoclef.tasks.construction.build_structure;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import baritone.utils.DirUtil;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The one build a companion started and did not finish, kept on disk so it outlives a restart.
 *
 * <p>{@link BuildPlanCache} already replays a plan rather than redesigning it, which is what makes
 * "go and get what is missing, then build again" terminate. But it lives in memory with a ten-minute
 * TTL, and the two things that most often interrupt a large build — quitting the game, and a long trip
 * to gather materials — are exactly the things that outlast it. Asked afterwards to carry on, the
 * model has nothing to go on but the conversation, so it invents a position: a measured case put a
 * half-built house at (180, 62, -40) and the "continuation" at (191, 64, -39), overlapping in 28 of
 * 550 cells. Nothing was miscounted; it was told to price a different building.
 *
 * <p>So the plan is written out whenever a build stops incomplete, and read back when one is asked for
 * again. There is deliberately no expiry: a half-built house is still standing there tomorrow, and the
 * record is cleared when the build finishes rather than on a timer.
 *
 * <p>One per companion, keyed by entity UUID — which survives a restart, unlike the controller
 * identity {@code BuildPlanCache} matches on. A companion working on a second structure has given up
 * on the first, and remembering both would only offer the model a choice it has no way to make.
 */
public final class UnfinishedBuild {

    private static final Logger LOGGER = LogManager.getLogger();

    /** Bumped if the on-disk shape changes; an unreadable or older record is discarded, not migrated. */
    private static final int FORMAT = 1;

    private UnfinishedBuild() {
    }

    /** What was left half-built, as read back from disk. */
    public record Record(String description, BlockPos anchor, String dimension, int placed, int total,
            List<SetBlockCommand> plan) {

        /**
         * One line for the agent's status, phrased as the instruction it needs to act on.
         *
         * <p>The position is stated <em>before</em> the quoted description and never appended to it.
         * An earlier version wrote {@code "<description>" at (x, y, z)}, and since the model is told to
         * reuse the quoted text exactly, it copied the suffix too — so the stored description grew
         * another " at (197, 61, -31)" on every resume. The description already carries its own
         * position, put there when the build was first asked for; repeating it outside the quotes is
         * for the reader, not for copying.
         */
        public String describeForAgent() {
            return String.format(
                    "UNFINISHED BUILD near (%d, %d, %d): %d of %d blocks are already up. To carry on "
                            + "with it, call build_structure with EXACTLY this description, copied "
                            + "verbatim and nothing added: \"%s\". Do NOT reword it, and do NOT append a "
                            + "position to it — a different description builds a second, separate "
                            + "structure beside this one and pays for it all over again.",
                    anchor.getX(), anchor.getY(), anchor.getZ(), placed, total, description);
        }
    }

    private static Path fileFor(UUID companion) {
        return DirUtil.getConfigDir().resolve("aicompanion").resolve("builds")
                .resolve(companion + ".json");
    }

    private static Optional<UUID> idOf(AltoClefController mod) {
        return mod == null || mod.getPlayer() == null
                ? Optional.empty()
                : Optional.of(mod.getPlayer().getUUID());
    }

    /**
     * Write down a build that stopped short. Replaces any previous record for this companion.
     *
     * @param placed how many of the plan's cells are already standing, counted at the site
     */
    public static void save(AltoClefController mod, String description, List<SetBlockCommand> plan,
            int placed) {
        Optional<UUID> id = idOf(mod);
        if (id.isEmpty() || description == null || plan == null || plan.isEmpty()) {
            return;
        }
        BlockPos anchor = anchorOf(description, plan);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("description", description);
        root.addProperty("dimension", dimensionOf(mod));
        root.addProperty("placed", placed);
        root.addProperty("total", plan.size());
        JsonObject anchorJson = new JsonObject();
        anchorJson.addProperty("x", anchor.getX());
        anchorJson.addProperty("y", anchor.getY());
        anchorJson.addProperty("z", anchor.getZ());
        root.add("anchor", anchorJson);
        JsonArray cells = new JsonArray();
        for (SetBlockCommand command : plan) {
            JsonArray cell = new JsonArray();
            cell.add(command.x);
            cell.add(command.y);
            cell.add(command.z);
            cell.add(command.blockName);
            cells.add(cell);
        }
        root.add("plan", cells);

        Path file = fileFor(id.get());
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                writer.write(root.toString());
            }
            MEMO.put(id.get(), new Record(description, anchor, dimensionOf(mod), placed, plan.size(),
                    List.copyOf(plan)));
            LOGGER.info("Remembered unfinished build ({}) for {}: {} of {} placed, at {}",
                    description, id.get(), placed, plan.size(), anchor);
        } catch (IOException e) {
            // Losing the record costs a resume, not a build. Never take the task down for it.
            // Drop the memo too: a stale in-memory hit would promise a resume the disk cannot honour
            // after a restart, which is worse than admitting now that there is nothing saved.
            MEMO.remove(id.get());
            LOGGER.warn("Could not write the unfinished build record to {} ({})", file, e.toString());
        }
    }

    /**
     * Last known record per companion, so the status feed does not hit the disk every turn.
     *
     * <p>{@link adris.altoclef.player2api.status.AgentStatus} calls {@link #recall} on every single
     * LLM turn, and a plan is hundreds of cells — re-reading and re-parsing tens of kilobytes of JSON
     * on the server thread for a line of prose is not a trade worth making. Every write goes through
     * {@link #save} or {@link #clear}, so this cannot drift behind the file.
     *
     * <p>{@code null} value means "checked, and there is nothing", which is the common case and worth
     * caching just as much as a hit.
     */
    private static final java.util.Map<UUID, Record> MEMO = new java.util.concurrent.ConcurrentHashMap<>();

    /** Sentinel for "looked, found nothing" — {@link java.util.concurrent.ConcurrentHashMap} takes no nulls. */
    private static final Record NONE = new Record("", BlockPos.ZERO, "", 0, 0, List.of());

    /** What this companion left half-built, if anything, in the dimension it is standing in. */
    public static Optional<Record> recall(AltoClefController mod) {
        Optional<UUID> id = idOf(mod);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Record memo = MEMO.computeIfAbsent(id.get(),
                companion -> readFrom(mod, companion).orElse(NONE));
        if (memo == NONE) {
            return Optional.empty();
        }
        // A plan pins absolute coordinates, which point somewhere else entirely one dimension over.
        // Checked on read rather than on load so the record survives a trip to the Nether.
        return memo.dimension().isEmpty() || memo.dimension().equals(dimensionOf(mod))
                ? Optional.of(memo)
                : Optional.empty();
    }

    private static Optional<Record> readFrom(AltoClefController mod, UUID companion) {
        Path file = fileFor(companion);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("format") || root.get("format").getAsInt() != FORMAT) {
                LOGGER.info("Discarding an unfinished build record in an older format: {}", file);
                clear(mod);
                return Optional.empty();
            }
            // Read whatever is on disk; the dimension is compared in recall(). Filtering here would
            // cache "nothing" against a companion that has merely stepped through a portal, and the
            // record would stay invisible after it came home.
            String dimension = root.has("dimension") ? root.get("dimension").getAsString() : "";
            JsonObject anchor = root.getAsJsonObject("anchor");
            List<SetBlockCommand> plan = new ArrayList<>();
            for (var element : root.getAsJsonArray("plan")) {
                JsonArray cell = element.getAsJsonArray();
                plan.add(new SetBlockCommand(cell.get(0).getAsInt(), cell.get(1).getAsInt(),
                        cell.get(2).getAsInt(), cell.get(3).getAsString()));
            }
            if (plan.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Record(
                    root.get("description").getAsString(),
                    new BlockPos(anchor.get("x").getAsInt(), anchor.get("y").getAsInt(),
                            anchor.get("z").getAsInt()),
                    dimension,
                    root.get("placed").getAsInt(),
                    root.get("total").getAsInt(),
                    plan));
        } catch (Exception e) {
            LOGGER.warn("Could not read the unfinished build record at {} ({}); discarding it",
                    file, e.toString());
            clear(mod);
            return Optional.empty();
        }
    }

    /**
     * The record for this request, if the companion's unfinished build is the one being asked about.
     *
     * <p>Matched on the anchor alone, within {@link BuildPlanCache}'s tolerance, and deliberately not
     * on the wording. The whole failure this exists to fix is the model rephrasing: "a 13×15 oak house
     * with simple roof and door" came back as "13x15 oak house using logs and planks", which shares no
     * signature at all. There is only ever one unfinished build per companion, so a build_structure
     * aimed within a chunk of it is asking about that one — and the status line tells the model to
     * quote the original description, which makes the common case exact anyway.
     */
    public static Optional<Record> recallFor(AltoClefController mod, String description) {
        Optional<Record> stored = recall(mod);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> asked =
                adris.altoclef.tasks.construction.build_structure.templates.DescriptionParser
                        .position(description);
        if (asked.isEmpty()) {
            // No position in the request at all: "finish the house" and nothing else. The one
            // unfinished build is the only thing it can mean.
            return stored;
        }
        BlockPos anchor = stored.get().anchor();
        boolean near = Math.abs(anchor.getX() - asked.get().getX()) <= ANCHOR_TOLERANCE
                && Math.abs(anchor.getY() - asked.get().getY()) <= ANCHOR_TOLERANCE
                && Math.abs(anchor.getZ() - asked.get().getZ()) <= ANCHOR_TOLERANCE;
        return near ? stored : Optional.empty();
    }

    /** How far the request may be aimed from the half-built structure and still mean it. */
    private static final int ANCHOR_TOLERANCE = 16;

    /** The build finished, or is no longer worth resuming. */
    public static void clear(AltoClefController mod) {
        idOf(mod).ifPresent(id -> {
            MEMO.put(id, NONE);
            try {
                if (Files.deleteIfExists(fileFor(id))) {
                    LOGGER.info("Cleared the unfinished build record for {}", id);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not clear the unfinished build record for {} ({})", id, e.toString());
            }
        });
    }

    private static String dimensionOf(AltoClefController mod) {
        try {
            return mod.getWorld().dimension().location().toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Same rule {@link BuildPlanCache} anchors on: the stated position, else where the plan starts. */
    private static BlockPos anchorOf(String description, List<SetBlockCommand> plan) {
        Optional<BlockPos> stated =
                adris.altoclef.tasks.construction.build_structure.templates.DescriptionParser
                        .position(description);
        if (stated.isPresent()) {
            return stated.get();
        }
        SetBlockCommand first = plan.get(0);
        return new BlockPos(first.x, first.y, first.z);
    }
}
