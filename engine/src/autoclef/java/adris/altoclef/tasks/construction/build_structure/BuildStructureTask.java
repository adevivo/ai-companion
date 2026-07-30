package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.player2api.ConversationHistory;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Prompts;
import adris.altoclef.player2api.utils.Utils;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.tasks.construction.build_structure.templates.TemplateLibrary;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetWithinRangeOfBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.VecUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a described structure, in three phases:
 *
 * <ol>
 * <li>{@link RequestLLMCode} — ask the model for a DSL program. <b>Skipped</b> when
 * {@code TemplateLibrary} recognises the description: a built-in generator produces the plan directly
 * and the whole codegen round-trip, prompt included, never happens.
 * <li>{@link GenerateBlockPlan} — run that program on a worker thread. Pure interpretation: it
 * produces a list of block placements and touches nothing in the world.
 * <li>{@link PlaceBlocks} — charge the companion's inventory for the plan, then write the blocks on
 * the server thread, spread over several ticks.
 * </ol>
 *
 * <p>Splitting plan from placement is what keeps world writes on the server thread. Running the
 * interpreter and the {@code setBlock} calls together on a worker thread raced the server's own
 * chunk access.
 */
public class BuildStructureTask extends Task {
    private static final int maxNumErrors = 2;
    /**
     * Placements per tick on the legacy instant path. Enough to finish a large build quickly without
     * stalling a tick — and, at ~250 blocks for a small house, fast enough that the whole thing appears
     * within one tick, which is what physical placement exists to fix.
     */
    private static final int LEGACY_BLOCKS_PER_TICK = 256;
    /** Ticks to wait for a walk to one standing position. Matches {@code FarmProcess}'s per-tile budget. */
    private static final int MAX_STATION_TICKS = 300;
    /** Failed standing positions tolerated before the rest of the build is placed from where we are. */
    private static final int MAX_STATION_FAILURES = 4;
    /** Times a cell may drift out of sight before it is written out of reach instead. */
    private static final int MAX_DEFERRALS = 3;
    /** Cap on cells queued per standing position, so one scan cannot monopolise a build. */
    private static final int MAX_STATION_WORK = 64;
    /** Cells the companion may break to get free of its own structure before giving up. */
    private static final int MAX_DIG_FREE = 8;
    /** Ticks between progress lines in the log. */
    private static final int WORK_LOG_TICKS = 200;
    /** Ticks between progress lines in the owner's chat (30s). Chat only — never fed to the model. */
    private static final int PROGRESS_TICKS = 600;
    private static Logger LOGGER = LogManager.getLogger();

    private boolean isDone = false;
    private String description;
    private AltoClefController mod;
    private Player2APIService service;
    private int numErrors;
    /** Last codegen/parse failure, so the give-up notice can say what kept going wrong. */
    private String lastError;

    private Task actuallyRunningTask;
    private ConversationHistory history;
    private LLMCompleter completer;

    /** The in-flight material gather, or null. Compared by identity in the phase dispatch. */
    private Task gatherTask;
    /** Plan held across a gather so placement resumes with the same (already ground-checked) blocks. */
    private List<SetBlockCommand> pendingPlan = List.of();
    /** One gather per build. Without this a shortfall that gathering cannot fix would loop forever. */
    private boolean gatherAttempted = false;

    /** What the held plan costs, so the gather can report what is still outstanding. */
    private BuildMaterials.Bill pendingBill;
    /** When the owner was last told how the gather is going. */
    private long lastProgressReport;
    /**
     * How often to report progress while collecting.
     *
     * <p>A correctly-catalogued gather is a genuinely long errand — one logged build needed 246 oak
     * planks, 51 cobblestone, 12 glass, a crafting table and a furnace, which is minutes of mining
     * during which the companion simply walks away. Silence for that long reads as a hang, and the
     * owner cancels a build that was working. Quiet enough not to spam chat, often enough to show it
     * is still going.
     */
    private static final long PROGRESS_INTERVAL_MILLIS = 30_000L;

    /**
     * The description trimmed for agent-facing text.
     *
     * <p>Skill-generated descriptions run to hundreds of characters — the 9x9 wheat field spec is
     * ~700 — and every notice quoting one in full is echoed back into the next LLM request through
     * {@code gameDebugMessages}. Three refusals in one session pushed roughly 2.5KB of duplicated
     * spec through the context window. The log keeps the full text; the model only needs enough to
     * know which build is being talked about.
     */
    private String shortDescription() {
        String text = description == null ? "" : description.strip();
        return text.length() <= 80 ? text : text.substring(0, 77).strip() + "...";
    }

    /**
     * Strips whatever packaging the model wrapped the DSL program in before the parser sees it.
     *
     * <p>The prompt asks for plain text and {@code completeConversationToString} no longer forces
     * JSON mode, but models wrap anyway — in {@code <think>} preamble, in ``` fences, or in a
     * {@code {"program": "..."}} object. Unwrapped, every one of those is a parse error on line 1 or
     * 2, identical on every retry, so the task burns all three attempts and gives up.
     */
    static String normalizeCode(String raw) {
        // Fences come off first: a fenced JSON wrapper has to look like JSON before we can unwrap it,
        // and the program inside the wrapper is sometimes fenced again on its own.
        String text = stripFences(Utils.stripReasoning(raw));
        // A lone JSON object almost always carries the program in a single string field.
        if (text.startsWith("{")) {
            try {
                JsonObject obj = new JsonParser().parse(text).getAsJsonObject();
                for (String key : new String[] { "program", "code", "dsl", "structure" }) {
                    if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                        text = stripFences(obj.get(key).getAsString());
                        break;
                    }
                }
            } catch (RuntimeException e) {
                // Not JSON after all — fall through and let the DSL parser report on the real text.
                LOGGER.debug("Code reply starts with '{' but is not a JSON object; parsing as-is");
            }
        }
        return text;
    }

    /** Removes a surrounding ```` ```dsl ... ``` ```` (or bare ```` ``` ````) fence. */
    private static String stripFences(String text) {
        return text.replaceAll("(?s)^\\s*```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```\\s*$", "").trim();
    }

    private class RequestLLMCode extends Task {
        // outer option: isDone, either: (left=code (success), right=errStr)
        Optional<Either<String, String>> llmResult = Optional.empty();

        @Override
        protected boolean isEqual(Task var1) {
            return var1 instanceof RequestLLMCode && ((RequestLLMCode) var1).llmResult == llmResult;
        }

        @Override
        protected void onStart() {
            // call LLM and either output err or code result.
            completer.processToString(service, history, codeResult -> {
                String code = normalizeCode(codeResult);
                LOGGER.info("LLM generated code={}", code);
                llmResult = Optional.of(Either.left(code));
            }, errStr -> {
                LOGGER.info("LLM Transport Error={}", errStr);
                llmResult = Optional.of(Either.right(errStr));
            }, false);
        }

        @Override
        protected void onStop(Task var1) {

        }

        @Override
        protected Task onTick() {
            return null;
        }

        @Override
        protected String toDebugString() {
            return String.format("Thinking about how to build structure with description (%s)", description);
        }

        @Override
        public boolean isFinished() {
            return llmResult.isPresent();
        }
    }

    /**
     * Interprets the DSL on a worker thread and collects the placements it emits. No world access
     * happens here, so running off-thread is safe.
     */
    private class GenerateBlockPlan extends Task {
        final String code;

        private final ExecutorService planThread;
        /** Written by the worker before {@link #result}, read by the server thread after it. */
        private volatile List<SetBlockCommand> plan = List.of();
        // outer Option: is done, inner option: is error
        volatile Optional<Optional<String>> result = Optional.empty();

        GenerateBlockPlan(String code) {
            this.code = code;
            this.planThread = Executors.newSingleThreadExecutor();
            planThread.submit(() -> {
                List<SetBlockCommand> collected = new ArrayList<>();
                StructureFromCode.buildStructureFromCode(code, collected::add, errStr -> {
                    result = Optional.of(Optional.of(errStr));
                }, () -> {
                    plan = collected;
                    result = Optional.of(Optional.empty());
                }, mod);
            });
            planThread.shutdown();
        }

        @Override
        protected boolean isEqual(Task var1) {
            return var1 == this;
        }

        @Override
        protected void onStart() {
        }

        @Override
        protected void onStop(Task var1) {
            planThread.shutdownNow();
        }

        @Override
        protected Task onTick() {
            return null;
        }

        @Override
        public boolean isFinished() {
            return result.isPresent();
        }

        @Override
        protected String toDebugString() {
            return String.format("Working out how to build (%s)", description);
        }
    }

    /**
     * Charges for the plan and writes it into the world. Runs entirely on the server thread, a few
     * hundred blocks per tick.
     */
    private class PlaceBlocks extends Task {
        private List<SetBlockCommand> plan;
        private int next = 0;
        /**
         * Phases of a physical build. Air first, then solids — see {@link #advanceStage} for why the
         * carve-outs cannot simply be interleaved.
         */
        private enum Stage { APPROACH, CARVE, WORK, DONE }

        private Stage stage = Stage.APPROACH;
        /** Resolved plan, index-for-index with {@link #plan}. Null entries are unresolvable block names. */
        private List<BuildOrder.Cell> cells = List.of();
        /** What the finished plan will have filled and carved. Built after the ground check. */
        private BuildOrder.Occupancy occ;
        /** Indices already handled — placed, skipped as already-correct, or given up on. */
        private final BitSet handled = new BitSet();
        /** Per-index count of "was in reach, then wasn't". Three strikes and the cell is placed remotely. */
        private final Map<Integer, Integer> deferrals = new HashMap<>();
        /** Cells broken to dig free of our own structure; re-placed later without being billed twice. */
        private final Set<BlockPos> paidFor = new HashSet<>();
        /** Standing positions that timed out. Never offered again for this build. */
        private final Set<BlockPos> badStations = new HashSet<>();
        /** Where the companion is standing to work, or null when one needs choosing. */
        private BlockPos station;
        /** Held across ticks: a jittering subtask restarts pathing every tick and never arrives. */
        private Task travelTask;
        /** Indices reachable from {@link #station}, computed once on arrival. */
        private final List<Integer> stationWork = new ArrayList<>();
        /** Lowest index not yet handled — the scan start, so finished courses are not rescanned. */
        private int cursor = 0;
        private int stationTicks;
        private int buildTicks;
        private int stationFailures;
        private int workLogTicks;
        private int progressTicks;
        /** Blocks written from out of reach after the companion gave up on getting to them. */
        private int remotePlaced;
        /** True once reaching the rest has been abandoned; the remainder is written from where we stand. */
        private boolean remoteMode;
        /** Cells still to break to get free of our own structure. */
        private final List<BlockPos> digRoute = new ArrayList<>();
        /** Cells broken so far to get free, against {@link #MAX_DIG_FREE}. */
        private int digsUsed;
        /** Position to plan index, for re-queuing a cell that had to be broken to escape. */
        private final Map<BlockPos, Integer> indexByPos = new HashMap<>();
        /** Whether {@link #checkGround()} already ran for this plan on a previous attempt. */
        private final boolean alreadyGroundChecked;
        /** Guards the behaviour stack: only pop what was actually pushed. */
        private boolean pushedBehaviour;
        /** Blocks actually written. Zero at the end means the build was a no-op, not a success. */
        private int changed = 0;
        /**
         * Why the build was given up on, or null to carry on. Reported to the agent verbatim, so it
         * has to read as an explanation of what to do next rather than a status code.
         */
        private String abortReason;
        /** The same outcome said plainly, for the owner's chat. Set wherever {@link #abortReason} is. */
        private String playerReason;
        /**
         * True when the plan is worth keeping for a retry — i.e. it failed only because the materials
         * were not there. A plan that missed the ground or produced nothing is a bad plan, and
         * regenerating it is the point.
         */
        private boolean planWorthKeeping;
        /** Non-empty when the build paused to go and collect what it was short of. */
        private List<ItemTarget> gatherTargets = List.of();
        /** What the whole plan costs, once priced. Null until the materials check has run. */
        private BuildMaterials.Bill bill;
        /** What was missing at pricing time, so the gather can say what it went for. */
        private Map<Item, Integer> shortfall;

        PlaceBlocks(List<SetBlockCommand> plan) {
            this(plan, false);
        }

        /**
         * @param alreadyGroundChecked true when this plan has been through {@link #checkGround()} on an
         *        earlier attempt. Re-checking a plan that is <em>partly built</em> gives a wrong answer:
         *        {@code groundOffset} looks for a solid block with air above it, and in a finished wall
         *        column there is none — the search runs 64 blocks down, gives up, and reports the
         *        structure's own roof as ground level. The resulting offset can exceed
         *        {@code MAX_LIFT} and refuse a build that is halfway done. Instant placement never
         *        produced half-built structures; paced placement does, routinely.
         */
        PlaceBlocks(List<SetBlockCommand> plan, boolean alreadyGroundChecked) {
            this.plan = plan;
            this.alreadyGroundChecked = alreadyGroundChecked;
        }

        /** Whether this build is placing blocks by hand rather than writing the plan out wholesale. */
        private boolean physical() {
            return BehaviorConfig.buildPhysicalPlacement;
        }

        /** What this build still needs collecting, or empty to carry on placing. */
        List<ItemTarget> gatherTargets() {
            return gatherTargets;
        }

        /** The plan as it will actually be placed — already shifted by the ground check. */
        List<SetBlockCommand> plan() {
            return plan;
        }

        @Override
        protected void onStart() {
            if (plan.isEmpty()) {
                // isFinished() is already true for an empty plan, so onTick never runs and the
                // no-op report below would be skipped — the task would just finish and the agent
                // would announce a structure the DSL never described a single block of.
                LOGGER.info("Build ({}) produced an empty plan — no blocks to place", description);
                abortReason = String.format(
                        "Built nothing for (%s): the generated plan contained no blocks at all. Nothing exists — do not describe it as built. Try again with a clearer description.",
                        shortDescription());
                playerReason = "I couldn't work out how to build that — nothing was placed.";
                return;
            }
            // Ground check first: a plan that is going to be thrown away must not cost anything.
            if (!alreadyGroundChecked && !checkGround()) {
                return;
            }
            // Collapse duplicate positions and order the plan for a body to build in. Must happen after
            // the ground check (which rewrites `plan`) and before the bill (which prices what is left).
            int rawSize = plan.size();
            plan = BuildOrder.normalise(plan);
            if (plan.size() != rawSize) {
                // Not a warning: a plan that writes the same cell twice is legal DSL. Worth logging
                // because it also makes the bill smaller than it used to be for the same description.
                LOGGER.info("Build ({}) plan had {} duplicate position(s) collapsed; {} cells remain",
                        description, rawSize - plan.size(), plan.size());
            }
            cells = BuildOrder.cellsOf(plan);
            if (!BehaviorConfig.buildCostsMaterials) {
                LOGGER.info("Building ({}), {} blocks, materials disabled by config", description, plan.size());
                beginPhysical();
                return;
            }
            bill = BuildMaterials.tally(plan);
            shortfall = BuildMaterials.shortfall(mod, bill);
            if (!shortfall.isEmpty()) {
                LOGGER.info("Not enough materials to build ({}), missing {}", description,
                        BuildMaterials.describe(shortfall));
                // First time short: go and collect, rather than bouncing it back to the model to
                // fetch one item per round trip. gatherAttempted stops this repeating forever.
                if (!gatherAttempted) {
                    gatherTargets = BuildMaterials.gatherTargets(bill);
                    if (!gatherTargets.isEmpty()) {
                        return;
                    }
                    LOGGER.info("Nothing in the shortfall can be gathered automatically");
                }
                // The design is fine, only the inventory is short — so hold onto it. Re-running the
                // same description will place this same structure instead of designing a new one
                // with a new shopping list, which is what made "go and get it" never terminate.
                planWorthKeeping = true;
                abortReason = String.format(
                        "Could not build (%s): not carrying enough materials. Still needed: %s. Use `get` to collect exactly those, then run the SAME build_structure description again — it will build this same design, so nothing else will be needed.",
                        shortDescription(), BuildMaterials.describe(shortfall));
                playerReason = String.format("I can't build that yet — I still need %s.",
                        BuildMaterials.describeForPlayer(shortfall));
                return;
            }
            LOGGER.info("Building ({}), {} blocks costing {}", description, plan.size(),
                    bill.isFree() ? "nothing" : BuildMaterials.describe(bill.consumed()));
            beginPhysical();
        }

        /**
         * Set up for a hands-on build: the occupancy model, and the pathing guard rails.
         *
         * <p>The three behaviour overrides are not optional garnish. Baritone defaults to
         * {@code allowBreak} and {@code allowPlace} both true, so left alone it will happily tunnel
         * through the wall it just built to shorten a path, and pillar up into cells the plan needs. The
         * protected-items call is the subtle one: baritone's pillaring draws from
         * {@code acceptableThrowawayItems} — dirt, cobblestone, netherrack — which are very often the
         * build material itself, and {@code Settings.getThrowawayItems} filters that list through
         * {@code isProtected}. Without it, a build the pre-flight check confirmed was affordable can run
         * out of materials because the walk to the far corner ate them.
         */
        private void beginPhysical() {
            if (!physical()) {
                return;
            }
            occ = BuildOrder.occupancyOf(cells);
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i) == null) {
                    // Unresolvable block name. Retire it now so it never holds the cursor up, and so the
                    // partial-build count at the end does not blame the companion for not reaching it.
                    handled.set(i);
                    continue;
                }
                indexByPos.put(cells.get(i).pos(), i);
            }
            mod.getBehaviour().push();
            pushedBehaviour = true;
            mod.getBehaviour().avoidBlockBreaking(pos -> occ.needsFilling(pos));
            mod.getBehaviour().avoidBlockPlacing(pos -> occ.touches(pos));
            if (bill != null && !bill.consumed().isEmpty()) {
                mod.getBehaviour().addProtectedItems(bill.consumed().keySet().toArray(new Item[0]));
            }
        }

        /**
         * Compare the plan against the terrain. Asymmetric on purpose: a buried plan is always a
         * mistake and is lifted onto the surface, while a plan above the ground is taken at face
         * value, because "sitting on top of the ground" is a one-block gap and indistinguishable
         * from a one-block error by size alone. Only the two hopeless cases are refused.
         *
         * @return false when the build has been abandoned
         */
        private boolean checkGround() {
            if (!BehaviorConfig.buildGroundCheck) {
                return true;
            }
            OptionalInt offset = BuildPlacement.groundOffset(mod, plan);
            if (offset.isEmpty()) {
                return true; // nothing comparable — all air, or the footprint is not loaded
            }
            // Positive: plan is below the terrain and must come up. Negative: it is above it.
            int dy = offset.getAsInt();
            if (dy > 0) {
                if (dy <= BuildPlacement.MAX_LIFT) {
                    LOGGER.info("Build plan for ({}) came out {} block(s) underground; lifting onto the surface",
                            description, dy);
                    plan = BuildPlacement.shifted(plan, dy);
                    return true;
                }
                return refuseGround(dy + " blocks underground", dy);
            }
            if (-dy > BuildPlacement.MAX_AIR_GAP) {
                return refuseGround(-dy + " blocks up in the air", dy);
            }
            // At the surface, or deliberately above it — build exactly what was asked for.
            return true;
        }

        /**
         * Abandon the build before anything is spent, telling the agent where the ground actually is.
         *
         * <p>The ground Y is derived from the same measurement that caused the refusal
         * ({@code planBaseY + dy}) rather than from the companion's own feet. Reporting the feet made
         * the message contradict itself — "28 blocks underground … ground level there is y=41" while
         * y=42 was what had just been asked for — and the model, told the ground was one block lower,
         * re-aimed one block lower and measured further out. Six identical refusals in one session.
         */
        private boolean refuseGround(String where, int dy) {
            OptionalInt base = BuildPlacement.planBaseY(plan);
            int feetY = Mth.floor(mod.getEntity().getY());
            int groundY = base.isPresent() ? base.getAsInt() + dy : feetY - 1;
            LOGGER.info("Refusing build ({}): plan sits {}, ground measured at y={}", description, where, groundY);
            abortReason = String.format(
                    "Could not build (%s): the plan came out %s, so nothing was placed and no materials were spent. Ground level there is y=%d and your feet are at y=%d — build again using those, and do not claim the structure exists.",
                    shortDescription(), where, groundY, feetY);
            playerReason = String.format(
                    "I can't build that here — the plan came out %s, so I didn't place anything. Ground is at y=%d.",
                    where, groundY);
            return false;
        }

        @Override
        protected Task onTick() {
            if (abortReason != null || !gatherTargets.isEmpty()) {
                return null;
            }
            // occ is built in onStart, so a null here means the config was flipped mid-build (a
            // /companion reload). Finish the way this build started rather than dereferencing nothing.
            if (physical() && occ != null) {
                return tickPhysical();
            }
            return legacyPlaceAll();
        }

        /**
         * The original instant path, unchanged, for {@code buildPhysicalPlacement = false}.
         *
         * <p>Kept verbatim on purpose: it is the rollback if paced building misbehaves, and a rollback
         * that has been "tidied up" is not a rollback.
         */
        private Task legacyPlaceAll() {
            int visited = 0;
            while (next < plan.size() && visited < LEGACY_BLOCKS_PER_TICK) {
                SetBlockCommand command = plan.get(next);
                Block block = BuildMaterials.resolveBlock(command.blockName);
                BlockPos pos = new BlockPos(command.x, command.y, command.z);
                // Already the right block? Placing it again would cost an item and change nothing.
                // Compared by Block rather than BlockState on purpose: re-running a farm build must
                // not reset crop age or farmland moisture — and then bill for having done so.
                if (mod.getWorld().getBlockState(pos).getBlock() == block) {
                    next++;
                    visited++;
                    continue;
                }
                Item cost = BehaviorConfig.buildCostsMaterials ? BuildMaterials.consumedItemFor(block) : null;
                if (cost != null && !BuildMaterials.consume(mod, cost, 1)) {
                    // Pre-flight said we could afford this, so something else emptied the
                    // inventory mid-build. Stop rather than carry on placing for free.
                    LOGGER.warn("Ran out of {} partway through building ({})", BuildMaterials.name(cost),
                            description);
                    abortReason = String.format(
                            "Stopped building (%s) partway: ran out of %s. The structure is incomplete. Use `get` to collect more, then build again.",
                            shortDescription(), BuildMaterials.name(cost));
                    playerReason = String.format("I ran out of %s partway through — the build is unfinished.",
                            BuildMaterials.name(cost).replace('_', ' '));
                    return null;
                }
                // 3 means send to clients (2) and notify neighbors/update block states (1).
                mod.getWorld().setBlock(pos, block.defaultBlockState(), 3);
                changed++;
                next++;
                visited++;
            }
            if (next >= plan.size() && changed == 0) {
                reportNoOp();
                return null;
            }
            setDebugState(String.format("Placed %d/%d blocks", next, plan.size()));
            return null;
        }

        /**
         * Every block was already what the plan asked for. Nothing was placed and nothing was spent, but
         * the task still "finishes" — so say so, or the agent announces a structure it did not build.
         * This is what a rebuild onto itself looks like.
         *
         * <p>Only ever call this when the whole plan really was inspected. Under paced placement
         * {@code changed == 0} can also mean "could not get to anything new", and reporting that as
         * "it was already there" would be exactly the sort of false success the surrounding notices
         * exist to prevent.
         */
        private void reportNoOp() {
            LOGGER.info("Build ({}) changed nothing — all {} blocks already in place",
                    description, plan.size());
            abortReason = String.format(
                    "Built nothing for (%s): every block was already in place, so no materials were spent and the world is unchanged. Tell the owner it was already there rather than claiming you built it. If they wanted it somewhere else, use a different position.",
                    shortDescription());
            playerReason = "That was already there — I didn't need to place anything.";
        }

        /**
         * One tick of a hands-on build.
         *
         * <p>The shape of this is dictated by {@code Task.tick}: the parent's {@code onTick} runs every
         * tick whether or not a subtask is active, and the returned subtask is ticked afterwards. Two
         * consequences drive everything below. Returning a <em>different</em> subtask object stops the
         * old one and starts the new one, so the travel task is held in a field — recomputing the
         * standing position every tick would cancel pathing every tick and the companion would never
         * move. And returning null stops the current subtask, so null is only returned on ticks where
         * there genuinely is no travelling to do.
         */
        private Task tickPhysical() {
            if (++buildTicks > maxBuildTicks()) {
                abortTimedOut();
                return null;
            }
            logProgress();

            if (stage == Stage.APPROACH) {
                if (!siteReady()) {
                    if (travelTask == null) {
                        travelTask = new GetWithinRangeOfBlockTask(siteAnchor(), approachRange());
                    }
                    setDebugState("Walking to the build site");
                    return travelTask;
                }
                travelTask = null;
                retireAlreadyCorrect();
                stage = Stage.CARVE;
            }

            // Standing at a chosen spot with work to do: place from here.
            if (station != null && travelTask != null && travelTask.isFinished()) {
                stationTicks = 0;
                int budget = Mth.clamp(BehaviorConfig.buildBlocksPerTick, 1, 64);
                while (budget-- > 0 && !stationWork.isEmpty()) {
                    if (!placeOne(stationWork.remove(0), false)) {
                        return null; // ran out of materials; abortReason is set
                    }
                }
                if (!stationWork.isEmpty()) {
                    setDebugState(placementDebug());
                    return null;
                }
                station = null;
                travelTask = null;
            }

            if (station == null) {
                if (!advanceStage()) {
                    return null;
                }
                if (!selectStation()) {
                    return null; // nothing reachable — remote fallback ran, or we finished
                }
                stationTicks = 0;
                travelTask = new GetToBlockTask(station);
            }

            if (++stationTicks > MAX_STATION_TICKS || travelTask.thisOrChildAreTimedOut()) {
                LOGGER.info("Build ({}) could not reach standing position {}; trying another",
                        description, station.toShortString());
                badStations.add(station);
                station = null;
                travelTask = null;
                stationFailures++;
                return null;
            }

            setDebugState(placementDebug());
            return travelTask;
        }

        private String placementDebug() {
            return String.format("Placing %d/%d blocks%s", handled.cardinality(), plan.size(),
                    station == null ? "" : " (from " + station.toShortString() + ")");
        }

        /** Overall tick budget, scaled to the size of the job with a hard ceiling of 20 minutes. */
        private int maxBuildTicks() {
            return Math.min(24_000, 400 + plan.size() * 30);
        }

        /** Middle of the structure's footprint, at its base — what "the build site" means for walking. */
        private BlockPos siteAnchor() {
            BlockPos min = occ.min();
            BlockPos max = occ.max();
            return new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
        }

        private int approachRange() {
            BlockPos min = occ.min();
            BlockPos max = occ.max();
            int halfSpan = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2;
            return Math.max(4, halfSpan + 3);
        }

        /**
         * Whether we are close enough, and the terrain is loaded enough, to start scanning.
         *
         * <p>The chunk check matters for frame time rather than correctness: {@code getBlockState} on a
         * server level force-loads synchronously, and the station scan plus the escape BFS touch
         * thousands of positions. Doing that across unloaded chunks in a single tick is a visible stall.
         */
        private boolean siteReady() {
            BlockPos anchor = siteAnchor();
            if (!mod.getEntity().blockPosition().closerThan(anchor, approachRange() + 2)) {
                return false;
            }
            return mod.getChunkTracker().isChunkLoaded(occ.min()) && mod.getChunkTracker().isChunkLoaded(occ.max());
        }

        /**
         * Mark every cell that is already the block the plan wants, in one pass, before any walking.
         *
         * <p>Two things go wrong without this. A rebuild onto an existing structure would make the
         * companion walk the whole thing to discover, cell by cell, that there was nothing to do. Worse,
         * any already-correct cell it could not find a standing position for would be counted as
         * unreached at the end, and the build would report itself as partly finished when in fact
         * nothing was missing — a false failure, which is as bad as a false success.
         *
         * <p>Run at the APPROACH → CARVE transition rather than in {@code onStart} because reading block
         * states force-loads chunks synchronously, and by this point the site is loaded.
         */
        private void retireAlreadyCorrect() {
            int alreadyThere = 0;
            for (int i = 0; i < cells.size(); i++) {
                BuildOrder.Cell cell = cells.get(i);
                if (cell == null || handled.get(i)) {
                    continue;
                }
                if (mod.getWorld().getBlockState(cell.pos()).getBlock() == cell.block()) {
                    handled.set(i);
                    alreadyThere++;
                }
            }
            if (alreadyThere > 0) {
                LOGGER.info("Build ({}): {} of {} cells are already correct; {} to place",
                        description, alreadyThere, plan.size(), plan.size() - alreadyThere);
            }
        }

        /** Whether an index belongs to the phase currently running. */
        private boolean inCurrentStage(int index) {
            BuildOrder.Cell cell = cells.get(index);
            if (cell == null) {
                return false;
            }
            return stage == Stage.CARVE ? cell.isAir() : !cell.isAir();
        }

        /**
         * Move CARVE → WORK → DONE when the current phase has nothing left, and finish the task.
         *
         * <p>Air is a phase of its own rather than just another sort key, for three reasons. It is what
         * makes the escape guarantee in {@code BuildStanding.hasEscape} unconditional — every carve-out a
         * route relies on has already happened by the time solids go up. It is also what stops the
         * companion being sealed inside a house cut into a hillside, where the doorway is planned as air
         * but is currently solid rock. And air is free in the cost model, so front-loading it costs
         * nothing.
         *
         * @return false when the build is over and the caller should stop
         */
        private boolean advanceStage() {
            while (true) {
                if (pendingIndex() >= 0) {
                    return true;
                }
                if (stage == Stage.CARVE) {
                    stage = Stage.WORK;
                    cursor = 0;
                    badStations.clear();
                    stationFailures = 0;
                    continue;
                }
                finishBuild();
                return false;
            }
        }

        /** Lowest unhandled index in the current phase, advancing {@link #cursor} past finished work. */
        private int pendingIndex() {
            // The cursor only ever advances past cells that are finished for good. A cell belonging to a
            // later phase is skipped by the scan below but must not move the cursor, or the phase that
            // owns it would never find it.
            while (cursor < plan.size() && handled.get(cursor)) {
                cursor++;
            }
            for (int i = cursor; i < plan.size(); i++) {
                if (!handled.get(i) && inCurrentStage(i)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Choose somewhere to stand and collect the work reachable from it.
         *
         * @return false when no station could be found and the remainder was written remotely instead
         */
        private boolean selectStation() {
            int focus = pendingIndex();
            if (focus < 0) {
                return false;
            }
            if (remoteMode || stationFailures >= MAX_STATION_FAILURES) {
                // Repeated failures can mean "nowhere left to stand", but they can also mean the
                // companion is shut inside what it has built. Check before writing the rest off.
                if (tryDigFree()) {
                    return false;
                }
                enterRemoteMode();
                placeRemoteBatch();
                return false;
            }

            BlockPos target = cells.get(focus).pos();
            Optional<BlockPos> chosen = BuildStanding.chooseStation(
                    mod, occ, target, mod.getEntity().blockPosition(), badStations);
            if (chosen.isEmpty()) {
                // Genuinely nowhere to stand for this cell — the middle of a wide roof, a ceiling under
                // solid rock. Hand it to the remote path rather than stalling the whole build on it.
                LOGGER.info("Build ({}): no standing position reaches {}; placing it from here",
                        description, target.toShortString());
                // Return value ignored deliberately: a false means it ran out of materials and has
                // already set abortReason, which isFinished() picks up on this same tick.
                placeOne(focus, true);
                return false;
            }

            station = chosen.get();
            collectStationWork(station);
            if (stationWork.isEmpty()) {
                // Selected on a margin that the exact test then rejected. Blacklist and try again.
                badStations.add(station);
                station = null;
                stationFailures++;
                return false;
            }
            return true;
        }

        /** Indices this station can see and reach, scanned once so the per-tick drain stays cheap. */
        private void collectStationWork(BlockPos feet) {
            stationWork.clear();
            Vec3 eyes = BuildReach.eyesAt(mod.getEntity(), feet);
            double reach = BuildReach.reach(mod) - BuildReach.SELECT_MARGIN;
            for (int i = cursor; i < plan.size() && stationWork.size() < MAX_STATION_WORK; i++) {
                if (handled.get(i) || !inCurrentStage(i)) {
                    continue;
                }
                BlockPos pos = cells.get(i).pos();
                if (!BuildReach.withinReach(eyes, pos, reach)) {
                    continue;
                }
                if (!BuildReach.hasLineOfSight(mod.getWorld(), mod.getEntity(), eyes, pos)) {
                    continue;
                }
                stationWork.add(i);
            }
        }

        /**
         * Write one cell.
         *
         * <p>{@code remote} skips the reach and sight checks, for the documented fallback where nothing
         * can get to the cell. Otherwise a cell that has drifted out of sight since the station was
         * scanned — placing one block can occlude the next — is <em>deferred</em> rather than failed, and
         * only goes remote after three deferrals so it cannot ping-pong forever.
         *
         * @return false only when the build must stop (out of materials)
         */
        private boolean placeOne(int index, boolean remote) {
            BuildOrder.Cell cell = cells.get(index);
            if (cell == null) {
                handled.set(index);
                return true;
            }
            BlockPos pos = cell.pos();

            // Already the right block? Placing it again would cost an item and change nothing. Compared
            // by Block rather than BlockState on purpose: re-running a farm build must not reset crop age
            // or farmland moisture — and then bill for having done so.
            if (mod.getWorld().getBlockState(pos).getBlock() == cell.block()) {
                handled.set(index);
                return true;
            }

            Vec3 eyes = mod.getEntity().getEyePosition();
            if (!remote) {
                boolean reachable = BuildReach.withinReach(eyes, pos, BuildReach.reach(mod))
                        && BuildReach.hasLineOfSight(mod.getWorld(), mod.getEntity(), eyes, pos);
                if (!reachable) {
                    int misses = deferrals.merge(index, 1, Integer::sum);
                    if (misses < MAX_DEFERRALS) {
                        return true; // try again from a later station
                    }
                    remote = true;
                }
            }

            // Never brick ourselves in: a solid block written into a cell our own body occupies is
            // suffocation damage and a stuck companion. Defer it until we have moved on.
            if (!cell.isAir() && bodyOccupies(pos)) {
                deferrals.merge(index, 1, Integer::sum);
                return true;
            }

            if (!cell.isAir()) {
                Item cost = BehaviorConfig.buildCostsMaterials ? BuildMaterials.consumedItemFor(cell.block()) : null;
                // Cells broken to dig ourselves free were paid for the first time round. BuildMaterials
                // has no refund path and must not get one — a give-back would let a build net items — so
                // the re-placement is simply not charged again.
                if (cost != null && !paidFor.remove(pos) && !BuildMaterials.consume(mod, cost, 1)) {
                    // Pre-flight said we could afford this, so something else emptied the
                    // inventory mid-build. Stop rather than carry on placing for free.
                    LOGGER.warn("Ran out of {} partway through building ({})", BuildMaterials.name(cost),
                            description);
                    abortReason = String.format(
                            "Stopped building (%s) partway: ran out of %s. The structure is incomplete. Use `get` to collect more, then build again.",
                            shortDescription(), BuildMaterials.name(cost));
                    playerReason = String.format("I ran out of %s partway through — the build is unfinished.",
                            BuildMaterials.name(cost).replace('_', ' '));
                    return false;
                }
            }

            performPlacement(pos, cell);
            handled.set(index);
            changed++;
            if (remote) {
                remotePlaced++;
            }
            return true;
        }

        /** Whether the companion's own body is in the way of this cell. */
        private boolean bodyOccupies(BlockPos pos) {
            for (BlockPos touching : WorldHelper.getBlocksTouchingPlayer(mod.getEntity())) {
                if (touching.equals(pos)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The write itself, plus the trappings that make it look like work: hold the block, turn to face
         * it, swing, and let the world hear it. The head turn is safe here precisely because placement
         * only ever happens while standing still, so it is not fighting pathing for the rotation.
         */
        private void performPlacement(BlockPos pos, BuildOrder.Cell cell) {
            BlockState previous = mod.getWorld().getBlockState(pos);
            if (!cell.isAir()) {
                Item held = BuildMaterials.consumedItemFor(cell.block());
                if (held != null) {
                    mod.getSlotHandler().forceEquipItem(held);
                }
            }
            mod.getBaritone().getLookBehavior().updateTarget(
                    RotationUtils.calcRotationFromVec3d(
                            mod.getEntity().getEyePosition(),
                            VecUtils.calculateBlockCenter(mod.getWorld(), pos),
                            new Rotation(mod.getEntity().getYRot(), mod.getEntity().getXRot())),
                    true);
            mod.getEntity().swing(InteractionHand.MAIN_HAND, true);

            // 3 means send to clients (2) and notify neighbors/update block states (1).
            mod.getWorld().setBlock(pos, cell.block().defaultBlockState(), 3);

            if (cell.isAir()) {
                // Vanilla block-break particles and sound, so carving a doorway reads as digging.
                mod.getWorld().levelEvent(null, 2001, pos, Block.getId(previous));
            } else {
                SoundType sound = cell.block().defaultBlockState().getSoundType();
                mod.getWorld().playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                mod.getWorld().gameEvent(GameEvent.BLOCK_PLACE, pos,
                        GameEvent.Context.of(mod.getEntity(), cell.block().defaultBlockState()));
            }
        }

        /**
         * Dig out of our own structure, one cell per tick, if we are genuinely shut in.
         *
         * <p>{@code BuildStanding.hasEscape} is supposed to make this unreachable, and for a build left
         * to itself it does. It is not proof against the world changing underneath us — the owner walling
         * a doorway up, terrain the plan did not anticipate — and being permanently stuck inside a house
         * is a bad enough outcome to warrant a way out.
         *
         * <p>Carved cells that the plan wants filled are re-queued and recorded in {@code paidFor}, so
         * they get placed again at the end without being charged twice. There is deliberately no refund
         * path in {@code BuildMaterials}: a give-back would make a build a way to net items.
         *
         * @return true when a cell was broken this tick
         */
        private boolean tryDigFree() {
            if (digsUsed >= MAX_DIG_FREE) {
                return false;
            }
            BlockPos feet = mod.getEntity().blockPosition();
            if (digRoute.isEmpty()) {
                if (BuildStanding.hasEscape(mod, occ, feet)) {
                    return false; // stuck for some other reason; not our problem to solve here
                }
                List<BlockPos> route = BuildStanding.escapeByDigging(mod, occ, feet, MAX_DIG_FREE - digsUsed);
                if (route.isEmpty()) {
                    return false;
                }
                LOGGER.warn("Build ({}): companion is sealed inside the structure; digging out via {} cell(s)",
                        description, route.size());
                mod.tellOwner("I've boxed myself in — digging out.");
                digRoute.addAll(route);
            }

            BlockPos carve = digRoute.remove(0);
            BlockState previous = mod.getWorld().getBlockState(carve);
            if (!previous.isAir()) {
                mod.getEntity().swing(InteractionHand.MAIN_HAND, true);
                mod.getWorld().setBlock(carve, Blocks.AIR.defaultBlockState(), 3);
                mod.getWorld().levelEvent(null, 2001, carve, Block.getId(previous));
            }
            digsUsed++;

            Integer index = indexByPos.get(carve);
            if (index != null && cells.get(index) != null && !cells.get(index).isAir()) {
                // Owed a re-place, and already paid for.
                handled.clear(index);
                paidFor.add(carve);
                cursor = Math.min(cursor, index);
            }
            // The route may no longer be valid once the world has changed; recompute next time.
            if (digRoute.isEmpty()) {
                badStations.clear();
                stationFailures = 0;
            }
            return true;
        }

        /** Announce, once, that the rest of the build is being finished at arm's length. */
        private void enterRemoteMode() {
            if (remoteMode) {
                return;
            }
            remoteMode = true;
            LOGGER.info("Build ({}): giving up on reaching the remainder after {} failed standing positions",
                    description, stationFailures);
            mod.tellOwner("I can't get to the rest of it — finishing from here.");
        }

        /** Write a tick's worth of the remainder without moving. */
        private void placeRemoteBatch() {
            int budget = Mth.clamp(BehaviorConfig.buildBlocksPerTick, 1, 64);
            while (budget-- > 0) {
                int index = pendingIndex();
                if (index < 0) {
                    return;
                }
                if (!placeOne(index, true)) {
                    return;
                }
            }
        }

        /** Periodic log line, and a much rarer line in the owner's chat. */
        private void logProgress() {
            if (++workLogTicks >= WORK_LOG_TICKS) {
                workLogTicks = 0;
                LOGGER.info("Build ({}): stage={} placed={}/{} cursor={} station={} remote={} failures={}",
                        description, stage, handled.cardinality(), plan.size(), cursor,
                        station == null ? "none" : station.toShortString(), remotePlaced, stationFailures);
            }
            if (++progressTicks >= PROGRESS_TICKS) {
                progressTicks = 0;
                mod.tellOwner(String.format("Building: %d/%d blocks placed.",
                        handled.cardinality(), plan.size()));
            }
        }

        /**
         * Wrap up. Anything the companion never got to is reported honestly — a partly-built structure
         * that the model announces as finished is the failure mode these notices exist to prevent, and
         * paced placement makes partial builds an ordinary outcome rather than an edge case.
         */
        private void finishBuild() {
            stage = Stage.DONE;
            int missed = 0;
            for (int i = 0; i < plan.size(); i++) {
                if (!handled.get(i) && cells.get(i) != null) {
                    missed++;
                }
            }
            if (changed == 0 && missed == 0) {
                reportNoOp();
                return;
            }
            if (missed > 0) {
                planWorthKeeping = true;
                abortReason = String.format(
                        "Stopped building (%s) after placing %d of %d blocks: I could not get to the remaining %d. The structure is partly built — say so rather than claiming it is finished. Running the SAME description again will resume this same plan and place only what is missing.",
                        shortDescription(), changed, plan.size(), missed);
                playerReason = String.format("I got %d of %d blocks placed — I couldn't reach the rest.",
                        changed, plan.size());
                return;
            }
            if (remotePlaced > 0) {
                LOGGER.info("Build ({}) finished; {} of {} block(s) had to be placed out of reach",
                        description, remotePlaced, changed);
            }
        }

        private void abortTimedOut() {
            stage = Stage.DONE;
            planWorthKeeping = true;
            LOGGER.warn("Build ({}) ran out of time after {} ticks with {}/{} placed",
                    description, buildTicks, handled.cardinality(), plan.size());
            abortReason = String.format(
                    "Stopped building (%s) after placing %d of %d blocks: it was taking too long. The structure is partly built — say so rather than claiming it is finished. Running the SAME description again will resume this same plan and place only what is missing.",
                    shortDescription(), changed, plan.size());
            playerReason = String.format("That build was taking too long — I stopped at %d of %d blocks.",
                    changed, plan.size());
        }

        /** Why the build stopped early, or null if it ran to completion. */
        String abortReason() {
            return abortReason;
        }

        /** The same, in words meant for the owner rather than the model. */
        String playerReason() {
            return playerReason;
        }

        /** Whether this plan should be held for a retry rather than regenerated. */
        boolean planWorthKeeping() {
            return planWorthKeeping;
        }

        /** What the whole plan costs, kept so the gather can report what is still outstanding. */
        BuildMaterials.Bill bill() {
            return bill;
        }

        /** What was missing when the build was priced, or null if it was affordable. */
        Map<Item, Integer> shortfall() {
            return shortfall;
        }

        /** Blocks actually written. Zero means the build was a no-op, not a success. */
        int changed() {
            return changed;
        }

        /**
         * Release the pathing guard rails, and stop any walk in progress.
         *
         * <p>The push is guarded by a flag because {@code onStart} has four early returns that never
         * reach it. A leaked {@code BotBehaviour} state keeps items protected and block breaking
         * forbidden for the rest of the session, which is a very quiet way to break everything the
         * companion does afterwards.
         *
         * <p>The cache write is new: interruption used to be unreachable in practice because a build
         * finished within a tick. Now that it takes minutes, the owner giving another order mid-build is
         * routine, and without this the partly-finished plan is lost and the next attempt pays to design
         * and gather for a fresh one.
         */
        @Override
        protected void onStop(Task interruptTask) {
            if (pushedBehaviour) {
                mod.getBehaviour().pop();
                pushedBehaviour = false;
            }
            if (travelTask != null) {
                mod.getBaritone().getPathingBehavior().forceCancel();
                travelTask = null;
            }
            if (interruptTask != null && physical() && stage != Stage.DONE && !plan.isEmpty()) {
                BuildPlanCache.remember(mod, description, plan);
            }
        }

        @Override
        protected boolean isEqual(Task var1) {
            return var1 == this;
        }

        @Override
        public boolean isFinished() {
            if (abortReason != null || !gatherTargets.isEmpty()) {
                return true;
            }
            return physical() ? stage == Stage.DONE : next >= plan.size();
        }

        @Override
        protected String toDebugString() {
            return String.format("Currently building the structure from description (%s)", description);
        }
    }

    public BuildStructureTask(String description, AltoClefController mod) {
        this.description = description;
        this.mod = mod;
        this.service = mod.getPlayer2APIService();
        this.numErrors = 0;
    }

    /**
     * Builds the codegen conversation on first use.
     *
     * <p>Lazy because the system prompt is ~17k characters and a templated build never sends it. It
     * used to be assembled in the constructor, so every build paid for it whether or not a model was
     * ever going to see it.
     */
    private void ensureCodegenReady() {
        if (history != null) {
            return;
        }
        history = new ConversationHistory(Prompts.getBuildStructurePrompt());
        history.addUserMessage(
                String.format("Build with the following description: (%s)", description),
                service);
        completer = new LLMCompleter();
    }

    @Override
    protected void onStart() {
        // A previous attempt at this exact request that only failed on materials left its plan
        // behind. Reuse it, so collecting what was missing actually finishes the job rather than
        // buying materials for a building that no longer exists.
        Optional<List<SetBlockCommand>> remembered = BuildPlanCache.recall(mod, description);
        if (remembered.isPresent()) {
            LOGGER.info("Build ({}) reusing the plan from the previous attempt: {} blocks",
                    description, remembered.get().size());
            // Ground-checked when it was first designed. Re-checking now would measure the part of it
            // that already exists — see the PlaceBlocks(List, boolean) contract.
            actuallyRunningTask = new PlaceBlocks(remembered.get(), true);
            return;
        }
        // Ordinary rectangular shapes are generated in-process. Only what the generators decline
        // reaches the model, which is where the codegen prompt's token cost is actually warranted.
        Optional<TemplateLibrary.Match> templated = TemplateLibrary.plan(description, mod);
        if (templated.isPresent()) {
            actuallyRunningTask = new PlaceBlocks(templated.get().plan());
            return;
        }
        ensureCodegenReady();
        actuallyRunningTask = new RequestLLMCode();
    }

    /**
     * Tell the owner how the material gather is going, at most once every
     * {@link #PROGRESS_INTERVAL_MILLIS}.
     *
     * <p>Chat only — the agent is deliberately left out of it. Feeding progress into the model would
     * spend tokens every half minute and invite it to narrate work the engine is already doing.
     */
    private void reportGatherProgress() {
        if (gatherTask == null || actuallyRunningTask != gatherTask || pendingBill == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastProgressReport < PROGRESS_INTERVAL_MILLIS) {
            return;
        }
        lastProgressReport = now;
        Map<Item, Integer> remaining = BuildMaterials.shortfall(mod, pendingBill);
        mod.tellOwner(remaining.isEmpty()
                ? "Got everything I need — starting the build."
                : "Still collecting: " + BuildMaterials.describeForPlayer(remaining) + " to go.");
    }

    @Override
    protected Task onTick() {
        if (numErrors > maxNumErrors) {
            // The task system only knows "finished", so without a notice the agent reads this as a
            // success and tells the player the structure is built. Say what actually happened.
            mod.logAgentNotice(String.format(
                    "Could not build (%s): the build plan failed to generate %d times in a row (last error: %s). Nothing was placed. Do not claim it was built.",
                    shortDescription(), numErrors, lastError == null ? "unknown" : lastError),
                    "Build failed: I couldn't come up with a workable plan for that. Nothing was placed.");
            isDone = true;
            return null;
        }
        if (actuallyRunningTask == null || !actuallyRunningTask.isFinished()) {
            reportGatherProgress();
            return actuallyRunningTask;
        }
        // ---------- now task is finished, switch to next task: -------

        if (actuallyRunningTask instanceof RequestLLMCode) {
            LOGGER.info("Requesting llm code for description={}", description);
            Either<String, String> result = ((RequestLLMCode) actuallyRunningTask).llmResult.get();
            // set actually running task to next task:
            result.mapBoth(
                    code -> {
                        LOGGER.info("LLM returned code={}", code);
                        actuallyRunningTask = new GenerateBlockPlan(code);
                        return null;
                    }, errStr -> {
                        ++numErrors;
                        lastError = errStr;
                        String tryAgainMessage = String.format(
                                "When trying to call the llm with the description, got this error: \n(%s)\n. Try again and generate code using the same description:\n(%s)",
                                errStr, description);
                        history.addUserMessage(tryAgainMessage, service);
                        LOGGER.info(tryAgainMessage);
                        actuallyRunningTask = new RequestLLMCode();
                        return null;
                    });
            return actuallyRunningTask;
        }
        if (actuallyRunningTask instanceof GenerateBlockPlan planTask) {
            Optional<String> result = planTask.result.get();
            // set actually running task in both cases
            result.ifPresentOrElse(
                    errStr -> {
                        ++numErrors;
                        lastError = errStr;
                        history.addAssistantMessage(planTask.code, service);
                        String tryAgainMessage = String.format(
                                "The code was executed, but got error \n(%s)\nTry again and generate code with the same description:\n(%s)",
                                errStr, description);
                        LOGGER.info(tryAgainMessage);
                        history.addUserMessage(tryAgainMessage, service);
                        actuallyRunningTask = new RequestLLMCode();
                    }, () -> {
                        actuallyRunningTask = new PlaceBlocks(planTask.plan);
                    });
            return actuallyRunningTask;
        }
        if (actuallyRunningTask == gatherTask && gatherTask != null) {
            // Collected (or gave up trying) — place the same plan again. gatherAttempted is already
            // set, so a shortfall this time round aborts instead of looping back here.
            LOGGER.info("Finished gathering for ({}); placing blocks", description);
            gatherTask = null;
            actuallyRunningTask = new PlaceBlocks(pendingPlan, true);
            return actuallyRunningTask;
        }
        if (actuallyRunningTask instanceof PlaceBlocks placeTask) {
            List<ItemTarget> needed = placeTask.gatherTargets();
            if (!needed.isEmpty()) {
                // Short of materials on the first attempt: go and get everything at once rather than
                // bouncing back to the model, which fetched one item per round trip and burned three
                // turns on a chest and a bucket.
                gatherAttempted = true;
                pendingPlan = placeTask.plan();
                pendingBill = placeTask.bill();
                gatherTask = needed.size() == 1
                        ? TaskCatalogue.getItemTask(needed.get(0))
                        : TaskCatalogue.getSquashedItemTask(needed.toArray(new ItemTarget[0]));
                if (gatherTask != null) {
                    LOGGER.info("Gathering materials for ({}): {}", description,
                            needed.stream().map(ItemTarget::toString).collect(Collectors.joining(", ")));
                    setDebugState("Collecting materials to build");
                    // Say so up front. This is where the companion disappears for minutes on end, and
                    // an owner who is not told what it is doing cancels it.
                    Map<Item, Integer> missing = placeTask.shortfall();
                    if (missing != null && !missing.isEmpty()) {
                        mod.tellOwner("Off to collect " + BuildMaterials.describeForPlayer(missing)
                                + " for the build — this may take a while.");
                    }
                    lastProgressReport = System.currentTimeMillis();
                    actuallyRunningTask = gatherTask;
                    return actuallyRunningTask;
                }
                // No usable resource task — fall through and let the next attempt report the shortfall.
                LOGGER.warn("Could not build a gather task for ({}); retrying placement to report it",
                        description);
                actuallyRunningTask = new PlaceBlocks(pendingPlan, true);
                return actuallyRunningTask;
            }
            // Missing materials or a plan that missed the ground are not the model's fault, so
            // there is nothing to regenerate — hand the agent the reason and let it act on it.
            String abortReason = placeTask.abortReason();
            if (abortReason != null) {
                LOGGER.info(abortReason);
                mod.logAgentNotice(abortReason, placeTask.playerReason());
            }
            if (abortReason == null && placeTask.changed() > 0) {
                // Ground truth from the engine, not the model's account of it. The owner has had a
                // build reported as finished that never placed a block; this is the line that is
                // only ever printed when blocks really went into the world.
                mod.tellOwner(String.format("Done — placed %d blocks.", placeTask.changed()));
            }
            if (placeTask.planWorthKeeping()) {
                BuildPlanCache.remember(mod, description, placeTask.plan());
            } else {
                // Built, or not worth rebuilding as drawn. Either way the next request for this
                // description should start fresh rather than replay a plan that is now done.
                BuildPlanCache.forget(mod, description);
            }
            isDone = true;
            actuallyRunningTask = null;
            return null;
        }
        LOGGER.error("actually running task in buildStructureTask set to incorrect type");
        return null;
    }

    @Override
    public boolean isFinished() {
        return isDone;
    }

    @Override
    protected boolean isEqual(Task other) {
        if (!(other instanceof BuildStructureTask))
            return false;
        BuildStructureTask o = (BuildStructureTask) other;
        return o.description == this.description;
    }

    @Override
    protected void onStop(Task next) {
    }

    @Override
    protected String toDebugString() {
        return "BuildingStructure(" + description + ")";
    }
}
