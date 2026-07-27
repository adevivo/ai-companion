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
    private boolean isProcessing = false;
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

    private MessageBuffer altoClefMsgBuffer = new MessageBuffer(10);

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

        // prepare conversation history for LLM call
        Event lastEvent = mod.getAIPersistantData().dumpEventQueueToConversationHistoryAndReturnLastEvent(eventQueue,
                mod.getPlayer2APIService());
        Optional<String> reminderString = getReminderStringFromLastEvent(lastEvent);

        String agentStatus = AgentStatus.fromMod(this.mod).toString();
        String worldStatus = WorldStatus.fromMod(this.mod).toString();
        String altoClefDebugMsgs = this.altoClefMsgBuffer.dumpAndGetString();
        ConversationHistory historyWithWrappedStatus = mod.getAIPersistantData()
                .getConversationHistoryWrappedWithStatus(worldStatus, agentStatus, altoClefDebugMsgs,
                        mod.getPlayer2APIService(), reminderString);

        LOGGER.info("[AICommandBridge/processChatWithAPI]: Calling LLM: history={}",
                new Object[] { historyWithWrappedStatus.toString() });

        Consumer<JsonObject> onLLMResponse = jsonResp -> {
            String llmMessage = Utils.getStringJsonSafely(jsonResp, "message");
            String command = this.isGreetingResponse ? "bodylang greeting"
                    : Utils.getStringJsonSafely(jsonResp, "command");
            this.isGreetingResponse = false;
            LOGGER.info("[AICommandBridge/processCharWithAPI]: Processed LLM repsonse: message={} command={}",
                    llmMessage, command);
            try {
                if (llmMessage != null || command != null) {
                    mod.getAIPersistantData().addAssistantMessage(llmMessage, mod.getPlayer2APIService());
                    onCharacterEvent.accept(new Event.CharacterMessage(llmMessage, command, this));
                } else {
                    LOGGER.warn(
                            "[AICommandBridge/processChatWithAPI/onLLMResponse]: Generated null llm message and command");
                }
            } catch (Exception e) {
                LOGGER.error("[AICommandBridge/processChatWithAPI/onLLMRepsonse: ERROR RUNNING SIDE EFFECTS, errMsg={}",
                        e.getMessage());
            } finally {
                this.isProcessing = false;
                requeueAfterMalformedReply(jsonResp, command);
            }
        };
        completer.processToJson(mod.getPlayer2APIService(), historyWithWrappedStatus, onLLMResponse, onErrMsg, true);
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
        LOGGER.info("queue for UUID={} name={} adding event={} ", getUUID(), getName(), event);
        eventQueue.add(event);
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

    public void onEvent(Event event) {
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
            if (eventQueue.isEmpty()) {

                LOGGER.info("adding cmd={} to queue because it finished and queue not empty", stopReason.commandName());
                addEventToQueue(new InfoMessage(String.format(
                        "Command feedback: %s finished running. What shall we do next? If no new action is needed to finish user's request, generate empty command `\"\"`.",
                        stopReason.commandName())));
            } else {
                LOGGER.info("Skipping command stop for cmd={} because queue not empty", stopReason.commandName());
            }
        } else if (stopReason instanceof CommandExecutionStopReason.Error) {
            LOGGER.info("adding cmd={} to queue because it errored", stopReason.commandName());
            addEventToQueue(new InfoMessage(String.format(
                    "Command feedback: %s FAILED. The error was %s.",
                    stopReason.commandName(),
                    ((CommandExecutionStopReason.Error) stopReason).errMsg())));
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
     * One-line dump of everything {@link #getPriority()} consults, so a companion that has queued a
     * message but is not acting on it can be diagnosed from a log rather than a debugger.
     */
    public String describeState() {
        return String.format("%s{enabled=%s, processing=%s, queued=%d, msSinceLastProcess=%d, priority=%d}",
                getName(), enabled, isProcessing, eventQueue.size(),
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