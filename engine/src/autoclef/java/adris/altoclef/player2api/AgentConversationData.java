package adris.altoclef.player2api;

import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import adris.altoclef.player2api.manager.ConversationManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.AgentSideEffects.CommandExecutionStopReason;
import adris.altoclef.player2api.Event.InfoMessage;
import adris.altoclef.player2api.brain.BrainTurnContext;
import adris.altoclef.player2api.status.AgentStatus;
import adris.altoclef.player2api.status.StatusUtils;
import adris.altoclef.player2api.status.WorldStatus;
import adris.altoclef.player2api.utils.Utils;
import net.minecraft.world.entity.LivingEntity;

public class AgentConversationData {

    private static short MAX_EVENT_QUEUE_SIZE = 10;

    public static final Logger LOGGER = LogManager.getLogger();

    private final AltoClefController mod;

    private final Deque<Event> eventQueue = new ConcurrentLinkedDeque<>();
    private long lastProcessTime = 0L;
    /**
     * Set on the server thread when a turn is dispatched and cleared on the LLM callback thread, so
     * it must be volatile: this is now the only thing stopping one conversation being processed
     * twice concurrently, and a stale read either double-dispatches or strands the companion.
     */
    private volatile boolean isProcessing = false;
    private boolean enabled = true;

    // Armed by onGreeting(), consumed by the turn that answers it. Both used to be initialized true
    // instead, which meant they were armed on construction whether or not a greeting was ever
    // requested — and nothing requests one (sendGreeting is unreferenced). The first real turn after
    // a companion spawns or has its brain re-attached on world load therefore had its command
    // silently rewritten to `bodylang greeting`: the companion waved, spoke the message it had
    // planned, and never ran the task the player actually asked for.
    // seperating these to be safe:
    private boolean isGreetingResponse = false;
    private boolean shouldIgnoreGreetingDance = false;
    /** Guards {@link #requeueAfterMalformedReply} so two bad replies cannot loop on each other. */
    private boolean lastReplyWasMalformed = false;

    /**
     * The last command+error seen, and how many times running it has failed identically.
     *
     * <p>A command that fails the same way twice will not start working on the third try, and the
     * retry is actively harmful: each attempt appends the command and its error to the conversation,
     * crowding out useful context and showing the model its own rejected output as precedent. One
     * session logged 39 consecutive identical failures on a malformed {@code goto} and 30 on a
     * command that does not exist.
     */
    private String lastFailureSignature = null;
    private int repeatedFailures = 0;
    private static final int MAX_REPEATED_FAILURES = 3;

    /**
     * Whether the turn currently in flight was self-triggered — driven only by {@code InfoMessage}
     * command feedback, with nobody having spoken to the companion.
     */
    private volatile boolean autonomousTurnInFlight = false;

    /**
     * Set when a real user message lands while an autonomous turn is already in flight. That turn was
     * composed without the message, so its command answers a question nobody is asking any more.
     *
     * <p>Seen live: the owner typed "kill that zombie" two seconds after a command-finish turn was
     * dispatched, the in-flight turn came back with `food 10`, and the companion walked off to forage
     * while the zombie was on top of them. The zombie was only dealt with a full round later.
     */
    private volatile boolean userMessagePreemptedTurn = false;

    /**
     * Turns taken on the companion's own initiative since anybody last spoke to it. Bounded by
     * {@link BehaviorConfig#maxAutonomousTurns}.
     */
    private volatile int consecutiveAutonomousTurns = 0;

    /**
     * When the currently-queued work first arrived, or 0 when the queue is empty. Drives
     * {@link #nanosWaiting()}, which is what decides whether a lock is a stall or just a companion
     * partway through a sentence.
     */
    private volatile long firstPendingSince = 0L;

    private MessageBuffer altoClefMsgBuffer = new MessageBuffer(10);

    /**
     * Why the running command did not do its job, or null. Consumed by the next
     * {@link #onCommandFinish} so the failure lands in the conversation history rather than only in
     * the rolling debug buffer. Written from the task thread, read on the server thread.
     */
    private volatile String pendingFailure = null;

    public AgentConversationData(AltoClefController mod) {
        this.mod = mod;
    }

    // ## Processing

    // 0 => should not process,
    // otherwise gives a number that increases based on higher priority
    // (for now it is #ns from last processing time)
    public long getPriority() {
        if (!enabled || isProcessing || eventQueue.isEmpty()) {
            return 0;
        }
        // Wait for this companion's own sentence to finish before composing the next one. Only its
        // own: another companion talking is no reason for this one to stop thinking.
        if (adris.altoclef.player2api.manager.TTSManager.isSpeaking(getUUID())) {
            return 0;
        }
        long sinceLast = System.nanoTime() - lastProcessTime;
        // behavior.thinkThrottleSeconds: rate-limit LLM turns. Returning 0 defers rather than drops —
        // the events stay queued and fold into the next turn once the window passes.
        double throttle = BehaviorConfig.thinkThrottleSeconds;
        if (throttle > 0 && lastProcessTime != 0L && sinceLast < (long) (throttle * 1_000_000_000L)) {
            return 0;
        }
        return sinceLast;
    }

    // get LLM response and add to conversation history
    public void process(
            Consumer<Event.CharacterMessage> onCharacterEvent,
            Consumer<String> extOnErrMsg,
            LLMCompleter completer) {

        if (isProcessing) {
            LOGGER.warn("Called queueData.process even though it was already processing! this should not happen");
            return;
        }
        if (eventQueue.isEmpty()) {
            LOGGER.warn("queueData.process called on empty event queue! this should not happen");
            return;
        }

        Consumer<String> onErrMsg = errMsg -> {
            this.isProcessing = false;
            extOnErrMsg.accept(errMsg);
        };

        this.lastProcessTime = System.nanoTime();
        this.isProcessing = true;

        // Classify the turn before the queue is drained: a batch of nothing but InfoMessages is the
        // companion prompting itself after a command finished, not anyone talking to it.
        this.autonomousTurnInFlight = eventQueue.stream().allMatch(evt -> evt instanceof InfoMessage);
        this.userMessagePreemptedTurn = false;
        if (this.autonomousTurnInFlight) {
            this.consecutiveAutonomousTurns++;
        }

        // prepare conversation history for LLM call
        Event lastEvent = mod.getAIPersistantData().dumpEventQueueToConversationHistoryAndReturnLastEvent(eventQueue,
                mod.getPlayer2APIService());
        // The queue has been drained into the history, so nothing is waiting any more. Anything that
        // arrives from here on starts its own wait clock.
        this.firstPendingSince = eventQueue.isEmpty() ? 0L : System.nanoTime();
        Optional<String> reminderString = getReminderStringFromLastEvent(lastEvent);

        String agentStatus = AgentStatus.fromMod(this.mod).toString();
        String worldStatus = WorldStatus.fromMod(this.mod).toString();
        String altoClefDebugMsgs = this.altoClefMsgBuffer.dumpAndGetString();

        // Everything the brain needs that is not the prompt, resolved HERE on the server thread
        // because most of it cannot be read anywhere else: WorldIdentity touches SavedData, and the
        // owner reference goes stale the moment a player reconnects. onLLMResponse below runs on a
        // completion thread and must not reach for any of it.
        String turnWorldId = mod.getOwner() == null ? null : WorldIdentity.idOf(mod.getWorld());
        final BrainTurnContext brainCtx = new BrainTurnContext(
                getUUID(),
                mod.getOwner() == null ? null : mod.getOwner().getUUID(),
                mod.getAIPersistantData().getCharacter().name(),
                mod.getOwnerUsername(),
                // Null on a self-prompted turn, which is what makes both memory paths skip it.
                this.autonomousTurnInFlight || lastEvent == null ? null : lastEvent.message(),
                turnWorldId,
                this.autonomousTurnInFlight,
                // Ingredients rather than a prompt. The local path ignores these and uses the
                // assembled history below; a client uses them to assemble its own, which is what
                // keeps its memories off the wire.
                mod.getAIPersistantData().rawHistory(),
                worldStatus,
                agentStatus,
                altoClefDebugMsgs,
                reminderString.orElse(null));

        // Whose key pays and whose memories are read — see BrainTransport. Local today; the seam is
        // what lets that become the owning client without touching this loop.
        final adris.altoclef.player2api.brain.BrainTransport brain = mod.getBrainTransport();
        java.util.List<String> memories = brain.recall(brainCtx);

        // Say out loud when memory has stopped working. Every failure in that path degrades to "no
        // memories" so that a dead embedder can never break a turn — which also makes an outage
        // completely invisible from the player's seat, and leaves the companion DENYING knowledge of
        // something it has stored. That reads as a broken feature rather than a broken endpoint, and
        // the only place it was ever reported was latest.log.
        //
        // Drained here because this is the server thread and the owner is resolved: MemoryHealth is
        // reported into from pool threads that have neither. At most one line per distinct problem
        // for as long as it lasts, so this is silent on essentially every turn.
        for (MemoryHealth.Notice notice : MemoryHealth.drain()) {
            mod.tellOwner(notice.text(), notice.problem());
        }

        ConversationHistory historyWithWrappedStatus = mod.getAIPersistantData()
                .getConversationHistoryWrappedWithStatus(worldStatus, agentStatus, altoClefDebugMsgs,
                        mod.getPlayer2APIService(), reminderString, memories);

        LOGGER.info("[AICommandBridge/processChatWithAPI]: Calling LLM: history={}",
                new Object[] { historyWithWrappedStatus.toString() });

        Consumer<JsonObject> onLLMResponse = jsonResp -> {
            String llmMessage = Utils.getStringJsonSafely(jsonResp, "message");
            String replied = this.isGreetingResponse ? "bodylang greeting"
                    : Utils.getStringJsonSafely(jsonResp, "command");
            this.isGreetingResponse = false;
            boolean preempted = this.userMessagePreemptedTurn && replied != null && !replied.isBlank();
            if (preempted) {
                // Keep the message so the companion still says something rather than going mute, but
                // do not act: the user's message is already queued and drives the very next turn, which
                // will decide what to actually do with full knowledge of what was asked.
                LOGGER.info("Dropping command={} from a self-triggered turn — a user message arrived while it was"
                        + " in flight and takes precedence.", replied);
            }
            this.userMessagePreemptedTurn = false;
            String command = preempted ? "" : replied;
            LOGGER.info("[AICommandBridge/processCharWithAPI]: Processed LLM repsonse: message={} command={}",
                    llmMessage, command);
            try {
                if (llmMessage != null || command != null) {
                    // ⚠️ The condition is an OR, so a command with no message reaches here with
                    // llmMessage still null — which is an ordinary turn, a companion acting without
                    // speaking, and exactly what a build issues dozens of. Storing that null wrote a
                    // JsonNull into the transcript that killed the NEXT turn's logging, and with it
                    // the server tick loop. Store the absence as an empty message instead.
                    mod.getAIPersistantData().addAssistantMessage(
                            llmMessage == null ? "" : llmMessage, mod.getPlayer2APIService());
                    onCharacterEvent.accept(new Event.CharacterMessage(llmMessage, command, this));
                    // Learn from the exchange only after the player has their answer, so a slow or dead
                    // extractor can never delay a reply. Returns immediately and does its own work
                    // async; no-op unless memory.extractionEnabled is on.
                    brain.learn(brainCtx, llmMessage);
                } else {
                    LOGGER.warn(
                            "[AICommandBridge/processChatWithAPI/onLLMResponse]: Generated null llm message and command");
                }
            } catch (Exception e) {
                LOGGER.error("[AICommandBridge/processChatWithAPI/onLLMRepsonse: ERROR RUNNING SIDE EFFECTS, errMsg={}",
                        e.getMessage());
            } finally {
                this.isProcessing = false;
                // Pass what the model actually replied, not the blanked-out value: a preempted turn is
                // not a malformed one, and the queued user message already guarantees a next turn.
                requeueAfterMalformedReply(jsonResp, replied);
            }
        };
        brain.submit(brainCtx, historyWithWrappedStatus, completer, onLLMResponse, onErrMsg);
    }

    /**
     * Give the agent one more turn when a malformed reply left it with nothing to do.
     *
     * <p>A reply that could not be parsed yields a message but an empty command, and nothing else
     * queues an event for it — so the loop simply stops. Observed live: the companion said "Water
     * ready—now building the 9x9 wheat field" and then stood still indefinitely, because that turn
     * was the entire remaining plan.
     *
     * <p>Only fires for the fallback path ({@link Player2APIService#FALLBACK_MARKER}), and only once
     * in a row: if the retry turn is also malformed, let it rest rather than spend requests in a
     * loop.
     */
    private void requeueAfterMalformedReply(JsonObject jsonResp, String command) {
        boolean wasFallback = jsonResp.has(Player2APIService.FALLBACK_MARKER);
        if (!wasFallback || (command != null && !command.isBlank())) {
            lastReplyWasMalformed = false;
            return;
        }
        if (lastReplyWasMalformed) {
            LOGGER.warn("Two malformed replies in a row; not queueing another retry turn.");
            lastReplyWasMalformed = false;
            return;
        }
        lastReplyWasMalformed = true;
        if (jsonResp.has(Player2APIService.TRUNCATED_MARKER)) {
            // Telling a truncated reply it was "not valid JSON" is false and unactionable — its JSON
            // was fine until the cap cut it off — so it re-sends the same over-long command and is
            // cut off again. Naming the real cause at least lets it shorten.
            LOGGER.info("Reply was cut off by the token limit; queueing a follow-up asking for a shorter one.");
            addEventToQueue(new Event.InfoMessage(
                    "Your last reply was CUT OFF by the output token limit before it finished, so NO "
                            + "command ran and nothing happened. Reply again and keep it short: put the "
                            + "command in the command field and leave reason and message brief."));
            // Only the player can fix the cap, and from their seat the companion just went quiet.
            getMod().tellOwner(String.format(
                    "⚠ %s's reply was cut off by the output token limit (llm.maxTokens=%d), so nothing ran. "
                            + "Set it to %d or more in /companion config.",
                    getName(), LlmConfig.maxTokens, LlmConfig.MIN_USEFUL_MAX_TOKENS));
            return;
        }
        LOGGER.info("Malformed reply produced no command; queueing a follow-up so the plan continues.");
        addEventToQueue(new Event.InfoMessage(
                "Your last reply was not valid JSON, so NO command ran and nothing happened. "
                        + "If you were part-way through a task, issue the command now. "
                        + "Reply with a JSON object containing reason, command and message."));
    }

    private boolean isEventDuplicateOfLastMessage(Event evt) {
        boolean isDuplicate = eventQueue.peekLast() != null && eventQueue.peekLast().equals(evt);
        if (isDuplicate) {
            LOGGER.warn("[EventQueueData]: evt={} was added twice!", evt.getConversationHistoryString());
            return true;
        }
        return false;
    }

    private void addEventToQueue(Event event) {
        if (isEventDuplicateOfLastMessage(event)) {
            return; // skip
        }
        if (eventQueue.size() > MAX_EVENT_QUEUE_SIZE) {
            eventQueue.removeFirst();
        }
        if (eventQueue.isEmpty()) {
            firstPendingSince = System.nanoTime();
        }
        LOGGER.info("queue for UUID={} name={} adding event={} ", getUUID(), getName(), event);
        eventQueue.add(event);

        // Start embedding now rather than when the turn dispatches. The event waits here for at
        // least a tick, and recall() runs on the SERVER THREAD — so this is what keeps a network
        // call off the tick loop. No-op unless memory is on and the gate accepts the turn.
        //
        // The owner goes in because the gate consults what they have stored, and it must reach the
        // same verdict here as recall() does later — otherwise the turn is admitted with no vector
        // waiting and pays the full embed against embedBudgetMs.
        if (event instanceof Event.UserMessage msg) {
            mod.getBrainTransport().prefetch(msg.message(),
                    mod.getOwner() == null ? null : mod.getOwner().getUUID());
        }
    }

    private Optional<String> getReminderStringFromLastEvent(Event lastEvent) {
        if (lastEvent instanceof Event.UserMessage) {
            return Optional.of(((Event.UserMessage) lastEvent).userName().equals(getMod().getOwnerUsername())
                    ? Prompts.reminderOnOwnerMsg
                    : Prompts.reminderOnOtherUSerMsg);
        }
        if (lastEvent instanceof Event.CharacterMessage) {
            return Optional.of(Prompts.reminderOnAIMsg);
        }
        return Optional.empty();
    }

    // ## Callbacks:
    public void addAltoclefLogMessage(String message) {
        LOGGER.info("Adding altoclef system msg={}", message);
        this.altoClefMsgBuffer.addMsg(message);
    }

    /**
     * Remember that the running command did not do what was asked, so {@link #onCommandFinish} can say
     * so in the event it queues.
     *
     * <p>Without this the outcome only ever lived in {@code altoClefMsgBuffer}, which is drained by
     * the read that renders {@code gameDebugMessages} — one turn of visibility — while the paired
     * "finished running" event stays in the history for good. The model then had a permanent record
     * that the command completed and no record of it having failed, and reported success.
     */
    public void recordCommandFailure(String message) {
        this.pendingFailure = message;
    }

    public void onEvent(Event event) {
        if (event instanceof Event.UserMessage) {
            // Somebody is talking to us: the companion is no longer running on its own initiative, so
            // it earns a fresh budget of self-triggered turns.
            consecutiveAutonomousTurns = 0;
            if (isProcessing && autonomousTurnInFlight) {
                LOGGER.info("User message arrived mid-turn; the in-flight self-triggered command will be discarded.");
                userMessagePreemptedTurn = true;
            }
        }
        addEventToQueue(event);
    }

    public void onAICharacterMessage(Event.CharacterMessage msg) {
        boolean comingFromThisCharacter = msg.sendingCharacterData().getUUID().equals(getUUID());
        // is our character <=> dont add because we will already have added assistant
        // msg
        if (comingFromThisCharacter) {
            return;
        }
        eventQueue.add(msg);
    }

    public void onGreeting() {
        // Arm the overrides here, not at construction: they must only affect the turn that answers
        // this greeting event, never whatever the player happens to ask for first.
        isGreetingResponse = true;
        shouldIgnoreGreetingDance = true;
        // queue up greeting
        addEventToQueue(mod.getAIPersistantData().getGreetingEvent());
        // Read the flag, then set it: they have met now, so the next greeting is a welcome back.
        // Recorded at the moment of greeting rather than when the reply lands, because a greeting
        // whose turn failed is still a meeting — and re-introducing itself on every restart until
        // one succeeds is the worse failure of the two.
        mod.getAIPersistantData().setMetOwner(true);
    }

    public void onCommandFinish(AgentSideEffects.CommandExecutionStopReason stopReason) {
        LOGGER.info("on command finish for cmd={}", stopReason.commandName());
        if (stopReason instanceof CommandExecutionStopReason.Finished) {
            LOGGER.info("on command={} finish case", stopReason.commandName());
            if (shouldIgnoreGreetingDance && stopReason.commandName().contains("bodylang greeting")) {
                LOGGER.info("Skipping on command finish because should ignore greeting dance");
                // ignore first greeting command finish:
                shouldIgnoreGreetingDance = false;
                return;
            } else {
                shouldIgnoreGreetingDance = false;
            }
            String failure = pendingFailure;
            pendingFailure = null;
            if (failure != null) {
                // A failure means the owner's request is still unmet, so this is not the companion
                // drifting off on its own — it is recovery, and it needs room to run. Without this
                // reset a gather -> build loop stalls halfway: the refused build queues (failures are
                // never capped), the follow-up `get` succeeds, and then the budget is already spent so
                // nothing prompts the retry.
                consecutiveAutonomousTurns = 0;
                // Queued whatever else is pending, unlike the plain "what next" prompt below: a
                // failure has to reach the conversation history, because that is the only record
                // that outlives the turn it happened on. Skipping it here is what let the agent
                // keep a permanent "finished" and no trace of the reason it had not.
                LOGGER.info("adding cmd={} finish to queue as a FAILURE: {}", stopReason.commandName(), failure);
                addEventToQueue(new InfoMessage(String.format(
                        "Command feedback: %s finished, but it did NOT do what was asked. %s Do not tell the owner it succeeded or that the result exists — say what actually happened and act on it. If nothing further is needed, generate empty command `\"\"`.",
                        stopReason.commandName(), failure)));
            } else if (eventQueue.isEmpty()) {
                if (autonomousBudgetSpent()) {
                    LOGGER.info("Not prompting for a next step after cmd={}: {} self-triggered turns already taken"
                            + " without anyone speaking. Waiting to be addressed.",
                            stopReason.commandName(), consecutiveAutonomousTurns);
                    return;
                }
                LOGGER.info("adding cmd={} to queue because it finished and queue not empty", stopReason.commandName());
                addEventToQueue(new InfoMessage(String.format(
                        "Command feedback: %s finished running. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                        stopReason.commandName())));
            } else {
                LOGGER.info("Skipping command stop for cmd={} because queue not empty", stopReason.commandName());
            }
        } else if (stopReason instanceof CommandExecutionStopReason.Error) {
            String failed = stopReason.commandName();
            String error = ((CommandExecutionStopReason.Error) stopReason).errMsg();
            String signature = failed + " " + error;
            if (signature.equals(lastFailureSignature)) {
                repeatedFailures++;
            } else {
                lastFailureSignature = signature;
                repeatedFailures = 1;
            }

            if (repeatedFailures >= MAX_REPEATED_FAILURES) {
                // Re-issuing a command that fails the same way is not going to start working, and the
                // retry is not free: every attempt appends the command and its error to the history,
                // which both crowds out useful context and shows the model its own bad output as
                // precedent to copy. Observed at 39 consecutive identical failures on one malformed
                // goto and 30 on a command that does not exist.
                LOGGER.warn("Command {} failed {} times with the same error; telling the agent to stop "
                        + "retrying it. Error was: {}", failed, repeatedFailures, error);
                addEventToQueue(new InfoMessage(String.format(
                        "Command feedback: %s has now FAILED %d times with the same error, so it will "
                                + "not work however it is phrased. STOP re-issuing it. The error was %s. "
                                + "Either use a DIFFERENT command from the valid list, or generate an "
                                + "empty command `\"\"` and tell the owner plainly that you cannot do this "
                                + "and what you tried.",
                        failed, repeatedFailures, error)));
                repeatedFailures = 0;
                lastFailureSignature = null;
            } else {
                LOGGER.info("adding cmd={} to queue because it errored", failed);
                addEventToQueue(new InfoMessage(String.format(
                        "Command feedback: %s FAILED. The error was %s.", failed, error)));
            }
        } else if ("@stop".equals(stopReason.commandName().trim())) {
            // A cancel only happens while an explicit `stop` is in flight, and it fires twice: once for
            // the task being torn down and once for `@stop` itself. Prompt for a next step on the
            // `@stop` callback only, so we queue one event rather than two.
            //
            // Without this the agent deadlocks: `stop` is a legitimate move when the model wants to
            // abandon a wrong task and start the right one, but nothing else refills the queue, so it
            // goes permanently silent mid-plan until the owner types in chat. Skip when the queue is
            // already non-empty (same rule as the finished case) so a real user message isn't preempted.
            if (eventQueue.isEmpty()) {
                if (autonomousBudgetSpent()) {
                    LOGGER.info("Not prompting for a next step after stop: {} self-triggered turns already taken"
                            + " without anyone speaking. Waiting to be addressed.", consecutiveAutonomousTurns);
                    return;
                }
                LOGGER.info("adding cmd={} to queue so the agent can continue after stopping",
                        stopReason.commandName());
                addEventToQueue(new InfoMessage(
                        "Command feedback: the previous task was stopped. If you stopped it to do something"
                                + " else, issue that command now. If the owner's request is already complete,"
                                + " generate empty command `\"\"`."));
            } else {
                LOGGER.info("Skipping stop follow-up for cmd={} because queue not empty",
                        stopReason.commandName());
            }
        } else {
            LOGGER.info("Skipping command stop for cmd={} because it was cancelled", stopReason.commandName());
        }
    }

    /**
     * Whether the companion has used up its run of self-triggered turns and should now wait to be
     * spoken to. Only gates the "what next?" prompts — a command <em>failure</em> is still always
     * reported, since that is information the owner needs and the model cannot otherwise learn.
     */
    private boolean autonomousBudgetSpent() {
        int max = BehaviorConfig.maxAutonomousTurns;
        return max > 0 && consecutiveAutonomousTurns >= max;
    }

    // Utils:
    public float getDistance(UUID target) {
        return StatusUtils.getDistanceToUUID(mod, target);
    }

    public UUID getUUID() {
        return mod.getPlayer().getUUID();
    }

    public AltoClefController getMod() {
        return mod;
    }

    public boolean isOwner(UUID playerToCheck) {
        return mod.isOwner(playerToCheck);
    }

    public LivingEntity getEntity() {
        return mod.getPlayer();
    }

    /** Whether messages are waiting to be sent to the model. */
    public boolean hasPendingEvents() {
        return !eventQueue.isEmpty();
    }

    /**
     * How long the oldest undispatched work has been waiting, in nanoseconds; 0 when nothing is
     * pending. This is the honest measure of a stall — "time since the last turn" is not, because a
     * conversation that has never taken a turn has no last turn, and one that just took a turn may
     * legitimately be holding new work while the companion finishes speaking.
     */
    public long nanosWaiting() {
        long since = firstPendingSince;
        return since == 0L ? 0L : System.nanoTime() - since;
    }

    /**
     * One-line dump of everything {@link #getPriority()} consults, so a companion that has queued a
     * message but is not acting on it can be diagnosed from a log rather than a debugger.
     */
    public String describeState() {
        return String.format(
                "%s{enabled=%s, processing=%s, autonomousInFlight=%s, autonomousTurns=%d, queued=%d,"
                        + " msWaiting=%d, msSinceLastProcess=%d, priority=%d}",
                getName(), enabled, isProcessing, autonomousTurnInFlight, consecutiveAutonomousTurns,
                eventQueue.size(), nanosWaiting() / 1_000_000L,
                lastProcessTime == 0L ? -1 : (System.nanoTime() - lastProcessTime) / 1_000_000L,
                getPriority());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Character getCharacter() {
        return mod.getAIPersistantData().getCharacter();
    }

    public Player2APIService getPlayer2apiService() {
        return mod.getPlayer2APIService();
    }

    public String getName() {
        return getCharacter().shortName();
    }

}