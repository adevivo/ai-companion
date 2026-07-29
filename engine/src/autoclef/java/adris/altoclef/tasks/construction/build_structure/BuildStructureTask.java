package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

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
    /** Placements per tick. Enough to finish a large build quickly without stalling a tick. */
    private static final int blocksPerTick = 256;
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
            this.plan = plan;
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
            if (!checkGround()) {
                return;
            }
            if (!BehaviorConfig.buildCostsMaterials) {
                LOGGER.info("Building ({}), {} blocks, materials disabled by config", description, plan.size());
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
            int visited = 0;
            while (next < plan.size() && visited < blocksPerTick) {
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
                // Every block was already what the plan asked for. Nothing was placed and nothing was
                // spent, but the task still "finishes" — so say so, or the agent announces a
                // structure it did not build. This is what a rebuild onto itself looks like.
                LOGGER.info("Build ({}) changed nothing — all {} blocks already in place",
                        description, plan.size());
                abortReason = String.format(
                        "Built nothing for (%s): every block was already in place, so no materials were spent and the world is unchanged. Tell the owner it was already there rather than claiming you built it. If they wanted it somewhere else, use a different position.",
                        shortDescription());
                playerReason = "That was already there — I didn't need to place anything.";
                return null;
            }
            setDebugState(String.format("Placed %d/%d blocks", next, plan.size()));
            return null;
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

        @Override
        protected void onStop(Task var1) {
        }

        @Override
        protected boolean isEqual(Task var1) {
            return var1 == this;
        }

        @Override
        public boolean isFinished() {
            return abortReason != null || !gatherTargets.isEmpty() || next >= plan.size();
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
            actuallyRunningTask = new PlaceBlocks(remembered.get());
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
            actuallyRunningTask = new PlaceBlocks(pendingPlan);
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
                actuallyRunningTask = new PlaceBlocks(pendingPlan);
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
