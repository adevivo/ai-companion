package adris.altoclef.player2api.manager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import adris.altoclef.eventbus.EventBus;
import adris.altoclef.player2api.AgentSideEffects;
import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.AgentConversationData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Event.UserMessage;
import adris.altoclef.player2api.status.StatusUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ChatMessage;
import net.minecraft.server.MinecraftServer;

public class ConversationManager {
    public static final Logger LOGGER = LogManager.getLogger();

    public static class Lock {
        public static boolean waitingForResponseLock = false; // prevents conversation processing before onLLMResponse
                                                              // called

        public static boolean isConversationLocked() {
            return waitingForResponseLock || TTSManager.isLocked();
        }
    }

    public static ConcurrentHashMap<UUID, AgentConversationData> queueData = new ConcurrentHashMap<>();
    public static final float messagePassingMaxDistance = 64; // let messages between entities pass iff <= this maximum
    private static boolean hasInit = false;

    public static void init() {
        if (!hasInit) {
            hasInit = true;
            // unused but need to keep this so subscribes to events
            // TODO: figure out what to do w. fabric here:
            ServerMessageEvents.CHAT_MESSAGE.register((ChatMessage) (evt, senderEntity, params) -> {
                // behavior.triggerPrefix: when set, only prefixed messages reach the brain (and the
                // prefix is stripped). Blank = respond to all nearby chat. This is the cheapest cost
                // control there is — an unaddressed message costs nothing.
                String message = BehaviorConfig.applyTriggerPrefix(evt.signedContent());
                if (message == null) {
                    return;
                }
                String sender = senderEntity.getName().getString();
                ConversationManager.onUserChatMessage(new UserMessage(message, sender));
            });
        }
    }

    private static List<LLMCompleter> llmCompleters = List.of(new LLMCompleter());

    // ## Utils
    public static AgentConversationData getOrCreateEventQueueData(AltoClefController mod) {
        return queueData.computeIfAbsent(mod.getPlayer().getUUID(), k -> {
            LOGGER.info(
                    "EventQueueManager/getOrCreateEventQueueData: creating new queue data for entId={}",
                    mod.getPlayer().getStringUUID());
            return new AgentConversationData(mod);
        });
    }

    /**
     * Tear down all cross-world state when a world stops.
     *
     * <p>Everything this clears is {@code static}, so in a single game process it survives from one
     * world session into the next. That is not merely a leak: a companion's entity UUID is persisted
     * in the world save, so on rejoin {@link #getOrCreateEventQueueData} finds the <em>previous</em>
     * session's entry under the same key and hands back an {@link AgentConversationData} still bound
     * to a discarded entity and an unloaded {@code ServerLevel}. Reaching through that during the next
     * world's player login is what hangs the server thread mid-load.
     *
     * <p>Registered on {@code SERVER_STOPPING} in {@code AltoClefController}'s static initialiser,
     * alongside the tick hook whose state this cleans up.
     */
    public static void onServerStopping() {
        int dropped = queueData.size();
        queueData.clear();
        Lock.waitingForResponseLock = false;
        // The completer list is static, so an in-flight request at shutdown would otherwise leave it
        // permanently "busy" and mute the companion for the rest of the game process.
        llmCompleters.forEach(LLMCompleter::reset);
        TTSManager.reset();
        Player2APIService.resetSessionCounters();
        EventBus.clear();
        LOGGER.info("ConversationManager/onServerStopping: cleared {} conversation(s), released locks, "
                + "reset session counters and event subscriptions", dropped);
    }

    /**
     * Drop a companion's conversation state. Must be called when its entity goes away — nothing else
     * removes from {@code queueData}, so without this a spawn/despawn cycle leaks an entry and leaves
     * stale data whose distance checks reference a discarded entity.
     */
    public static void forget(UUID companionUuid) {
        if (queueData.remove(companionUuid) != null) {
            LOGGER.info("ConversationManager/forget: dropped conversation data for {}", companionUuid);
        }
    }

    private static Stream<AgentConversationData> filterQueueData(Predicate<AgentConversationData> pred) {
        return queueData.values().stream().filter(pred);
    }

    private static Stream<AgentConversationData> getCloseDataByUUID(UUID sender) {
        return filterQueueData(data -> data.getDistance(sender) < messagePassingMaxDistance);
    }

    // ## Callbacks (need to register these externally)

    // register when a user sends a chat message
    public static void onUserChatMessage(UserMessage msg) {
        LOGGER.info("User message event={}", msg);
        // will add to entities close to the user:
        int delivered = 0;
        StringBuilder diagnostics = new StringBuilder();
        for (AgentConversationData data : queueData.values()) {
            float distance = StatusUtils.getDistanceToUsername(data.getMod(), msg.userName());
            boolean close = distance < messagePassingMaxDistance;
            diagnostics.append(String.format("[%s distance=%.1f withinRange=%s %s] ",
                    data.getName(), distance, close, describeWorldBinding(data)));
            if (close) {
                data.onEvent(msg);
                delivered++;
            }
        }
        if (delivered == 0) {
            // Silence here is otherwise indistinguishable from the model being down: the message is
            // logged on arrival and then simply never acted on.
            LOGGER.warn("ConversationManager: message from {} reached no companion "
                            + "({} in queueData) — {}",
                    msg.userName(), queueData.size(),
                    diagnostics.length() == 0 ? "queueData is empty" : diagnostics.toString());
        }
    }

    // register when an AI character messages
    public static void onAICharacterMessage(Event.CharacterMessage msg, UUID senderId) {
        UUID sendingUUID = msg.sendingCharacterData().getUUID();
        getCloseDataByUUID(sendingUUID).filter(data -> !(data.getUUID().equals(senderId)))
                .forEach(data -> {
                    LOGGER.info("onCharMsg/ msg={}, sender={}, running onCharMsg for ={}", msg.message(), senderId,
                            data.getName());
                    data.onAICharacterMessage(msg);
                });
    }

    /**
     * Identify exactly which world and entity a conversation is bound to.
     *
     * <p>A distance of {@code Float.MAX_VALUE} means {@code getDistanceToUsername} could not find the
     * speaker in {@code mod.getWorld().players()}. That has two very different causes — the conversation
     * is holding a stale {@code ServerLevel} from a previous session, or the world is live and the name
     * lookup is failing — and they need opposite fixes. The identity hash distinguishes them: a level
     * that differs from the one the player is actually in proves staleness.
     */
    private static String describeWorldBinding(AgentConversationData data) {
        try {
            var world = data.getMod().getWorld();
            var companion = data.getMod().getPlayer();
            return String.format("companionEntityId=%d removed=%s level=%s@%08x levelPlayers=%s",
                    companion == null ? -1 : companion.getId(),
                    companion != null && companion.isRemoved(),
                    world.dimension().location(),
                    System.identityHashCode(world),
                    world.players().stream().map(p -> p.getName().getString()).collect(Collectors.toList()));
        } catch (Exception e) {
            return "world binding unavailable: " + e;
        }
    }

    /** Throttle for {@link #reportStallIfWorkPending} so a stuck state logs periodically, not per tick. */
    private static long lastStallReport = 0L;

    /**
     * Warn when messages are queued but nothing is dispatching them. Only fires if work is actually
     * pending, and at most once every 5 seconds, so a healthy idle server stays quiet.
     */
    private static void reportStallIfWorkPending(String reason) {
        if (queueData.values().stream().noneMatch(AgentConversationData::hasPendingEvents)) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastStallReport < TimeUnit.SECONDS.toNanos(5)) {
            return;
        }
        lastStallReport = now;
        String states = queueData.values().stream()
                .map(AgentConversationData::describeState)
                .collect(Collectors.joining(", "));
        LOGGER.warn("ConversationManager: messages are queued but not being processed — {}. State: {}",
                reason, states);
    }

    private static void process(Consumer<Event.CharacterMessage> onCharacterEvent, Consumer<String> onErrEvent) {
        Optional<AgentConversationData> dataToProcess = queueData.values().stream().filter(data -> {
            return data.getPriority() != 0;
        }).max(Comparator.comparingLong(AgentConversationData::getPriority));
        boolean anyCompleterFree = llmCompleters.stream().anyMatch(LLMCompleter::isAvailible);
        if (dataToProcess.isEmpty()) {
            reportStallIfWorkPending("no conversation had a non-zero priority");
        } else if (!anyCompleterFree) {
            reportStallIfWorkPending("every LLM completer reports itself busy");
        }
        llmCompleters.stream().filter(LLMCompleter::isAvailible).forEach(completer -> {
            dataToProcess.ifPresent(data -> {
                data.process(onCharacterEvent, onErrEvent, completer);
            });
        });
    }

    // side effects are here:
    public static void injectOnTick(MinecraftServer server) {
        if (!hasInit) {
            init();
        }

        Consumer<Event.CharacterMessage> onCharacterEvent = (data) -> {
            AgentSideEffects.onEntityMessage(server, data);
        };
        Consumer<String> onErrEvent = (errMsg) -> {
            AgentSideEffects.onError(server, errMsg);
        };

        if (!Lock.isConversationLocked()) {
            process(onCharacterEvent, onErrEvent);
        } else {
            reportStallIfWorkPending(String.format("conversation is locked (waitingForResponse=%s, tts=%s)",
                    Lock.waitingForResponseLock, TTSManager.isLocked()));
        }

        TTSManager.injectOnTick(server);
    }

    public static void sendGreeting(AltoClefController mod, Character character) {
        LOGGER.info("Sending greeting character={}", character);
        AgentConversationData data = getOrCreateEventQueueData(mod);
        data.onGreeting();
    }

    public static void resetMemory(AltoClefController mod) {
        mod.getAIPersistantData().clearHistory();
    }

    private static boolean isCloseToPlayer(AgentConversationData data, String userName) {
        return StatusUtils.getDistanceToUsername(data.getMod(), userName) < messagePassingMaxDistance;
    }
}