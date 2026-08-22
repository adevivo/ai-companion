package adris.altoclef.player2api.manager;

import java.util.ArrayList;
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
import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.Event;
import adris.altoclef.player2api.LLMCompleter;
import adris.altoclef.player2api.PlayerPreferences;
import adris.altoclef.player2api.ServerPolicy;
import adris.altoclef.player2api.AgentConversationData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Event.UserMessage;
import adris.altoclef.player2api.status.StatusUtils;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ChatMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class ConversationManager {
    public static final Logger LOGGER = LogManager.getLogger();

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
                //
                // The SPEAKER's prefix, not the server's. It is a client-owned setting, and with
                // the brain on the client it is the speaker who pays for every turn it lets
                // through — so it is their call whether ambient chatter costs them anything. Falls
                // back to the server-wide value for a client that announced nothing.
                String message = PlayerPreferences.applyTriggerPrefix(
                        senderEntity.getUUID(), evt.signedContent());
                if (message == null) {
                    return;
                }
                String sender = senderEntity.getName().getString();
                for (Component notice : ConversationManager.onUserChatMessage(
                        new UserMessage(message, sender, false, senderEntity.getUUID()))) {
                    senderEntity.sendSystemMessage(notice);
                }
            });
        }
    }

    /**
     * Shared pool of LLM workers, one request in flight each.
     *
     * <p>Sized rather than one-per-companion on purpose. A local llama.cpp serves requests one at a
     * time regardless, hosted providers rate-limit bursts, and an unbounded fan-out across a roster
     * is how a paid endpoint produces a surprise bill. The cap is what bounds concurrent spend;
     * {@link AgentConversationData#getPriority()} — longest-waiting wins — is what keeps it fair, and
     * that only works if slots are contended rather than dedicated.
     *
     * <p>Workers are stateless with respect to the endpoint: the {@code Player2APIService} travels
     * with each request, so a future per-companion endpoint needs no change here.
     */
    private static volatile List<LLMCompleter> llmCompleters = newPool(LlmConfig.maxConcurrentRequests);

    private static List<LLMCompleter> newPool(int size) {
        int bounded = Math.max(1, Math.min(size, 16));
        List<LLMCompleter> pool = new ArrayList<>(bounded);
        for (int i = 0; i < bounded; i++) {
            pool.add(new LLMCompleter());
        }
        LOGGER.info("ConversationManager: LLM pool sized {}", bounded);
        return pool;
    }

    /** Resize the pool when {@code llm.maxConcurrentRequests} changes on a config reload. */
    public static void resizePool(int size) {
        int bounded = Math.max(1, Math.min(size, 16));
        if (bounded != llmCompleters.size()) {
            llmCompleters = newPool(bounded);
        }
    }

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
        // Before anything is cleared: the periodic save bounds what a crash can lose, but an orderly
        // quit should lose nothing, and quitting to the title screen stops the server every time. A
        // failure here must not stop the rest of the shutdown from running.
        int flushed = 0;
        for (AgentConversationData data : queueData.values()) {
            try {
                if (data.getMod() != null && data.getMod().getAIPersistantData() != null) {
                    data.getMod().getAIPersistantData().flushHistory();
                    flushed++;
                }
            } catch (Throwable e) {
                LOGGER.warn("ConversationManager/onServerStopping: could not save history for {} ({})",
                        data.getName(), e.toString());
            }
        }
        queueData.clear();
        lastEarshotNotice.clear();
        // The pool is static, so an in-flight request at shutdown would otherwise leave a slot
        // permanently "busy" and shrink the pool for the rest of the game process.
        llmCompleters.forEach(LLMCompleter::reset);
        TTSManager.reset();
        Player2APIService.resetSessionCounters();
        EventBus.clear();
        LOGGER.info("ConversationManager/onServerStopping: saved history for {} of {} conversation(s), "
                + "released locks, reset session counters and event subscriptions", flushed, dropped);
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

    /**
     * Whether this speaker is allowed to drive this companion at all.
     *
     * <p>Routing used to be pure proximity, which is fine in singleplayer and wrong the moment a
     * second person is on the server: any player within 64 blocks could give any companion an
     * instruction. What made that more than a nuisance is who pays. The turn is billed to the
     * companion's <b>owner</b> — with {@code llm.clientBrain} on, to the owner's own machine — and
     * the sentence the stranger typed is then handed to the extractor with the <em>owner's</em> UUID
     * and username attached, so it is learned as something the owner said. Neither is visible from
     * anybody's seat until the bill or the corpus is inspected.
     *
     * <p>An ownerless companion (spawned from the console, never bound to a player) answers nobody
     * here. It is still reachable through {@code /companion}, which has an operator path; chat has
     * none, and guessing an owner is how one gets adopted by whoever walks past.
     *
     * <p>{@link ServerPolicy#companionsAnswerAnyone} restores the old behaviour for a server where
     * everyone is trusted.
     */
    private static boolean mayAddress(AgentConversationData data, UUID speaker) {
        if (ServerPolicy.companionsAnswerAnyone) {
            return true;
        }
        if (speaker == null || data == null || data.getMod() == null) {
            return false;
        }
        return data.getMod().isOwner(speaker);
    }

    // ## Callbacks (need to register these externally)

    /**
     * Words that address the whole roster at once, in place of a companion's name.
     *
     * <p>Deliberately short. All four are ordinary English openers ("all good", "both of us made it"),
     * so they only count when followed by a comma or colon — see {@code stripAddressedName}'s
     * {@code requireSeparator}. A false positive here is the expensive direction: it fans one message
     * out to every companion and pays for a reply from each.
     */
    private static final List<String> BROADCAST_NAMES = List.of("all", "everyone", "both", "team");

    /**
     * Deliver a chat line to exactly one companion: the one it is addressed to by name, or failing
     * that the nearest one within {@link #messagePassingMaxDistance}. {@code "all:"} and its synonyms
     * ({@link #BROADCAST_NAMES}) address every companion in range at once.
     *
     * <p>It used to go to <em>every</em> companion in range unconditionally. With one companion out
     * that is the same thing; with two it doubles the cost of every instruction and gets two answers
     * to a question asked once. Addressing by name is how you tell them apart — {@code "Ava, go and
     * mine"} reaches Ava and nobody else, and the name is stripped before the model sees it. The
     * broadcast form brings back the fan-out for the cases that want it, but as something you ask for
     * rather than the default, and each recipient is told the line went to all of them so they can
     * divide the work instead of both doing it.
     *
     * @return lines to show the speaker: that nobody was close enough to hear them, or that
     *         {@code llm.maxTokens} is set too low for the companion to answer properly. Both are
     *         states where the companion otherwise appears to work and simply does nothing.
     */
    public static List<Component> onUserChatMessage(UserMessage msg) {
        LOGGER.info("User message event={}", msg);

        // One pass over every conversation: the delivery decision, the diagnostics and the earshot
        // notice all need the same distances, and getDistanceToUsername walks the world's player list.
        AgentConversationData nearestData = null;
        float nearest = Float.MAX_VALUE;
        AgentConversationData addressedData = null;
        String addressedBody = null;
        float addressedDistance = Float.MAX_VALUE;
        StringBuilder diagnostics = new StringBuilder();
        for (AgentConversationData data : queueData.values()) {
            float distance = StatusUtils.getDistanceToUsername(data.getMod(), msg.userName());
            boolean close = distance < messagePassingMaxDistance;
            boolean mine = mayAddress(data, msg.speakerUuid());
            diagnostics.append(String.format("[%s distance=%.1f withinRange=%s yours=%s %s] ",
                    data.getName(), distance, close, mine, describeWorldBinding(data)));
            // Someone else's companion is not merely out of range — it is not a candidate at all,
            // for the name match or the nearest-wins fallback. Skipped after the diagnostics line so
            // "why did nothing answer me" is still answerable from the log.
            if (!mine) {
                continue;
            }
            if (distance < nearest) {
                nearest = distance;
                nearestData = data;
            }
            // Matched even when out of range, so the earshot notice can name who was being called
            // rather than whoever happens to be closest.
            String body = stripAddressedName(msg.message(), data.getName());
            if (body != null && distance < addressedDistance) {
                addressedData = data;
                addressedBody = body;
                addressedDistance = distance;
            }
        }

        // A real name always wins, so a companion called "All" still answers to its own name and the
        // keyword only applies when it addresses nobody in particular.
        if (addressedData == null) {
            List<Component> broadcast = deliverBroadcast(msg, diagnostics);
            if (broadcast != null) {
                return broadcast;
            }
        }

        // Addressed by name wins over merely being closest, so you can talk to one standing behind you.
        boolean addressed = addressedData != null;
        AgentConversationData target = addressed ? addressedData : nearestData;
        float targetDistance = addressed ? addressedDistance : nearest;
        boolean delivered = target != null && targetDistance < messagePassingMaxDistance;
        if (delivered) {
            target.onEvent(addressed
                    ? new UserMessage(addressedBody, msg.userName(), false, msg.speakerUuid())
                    : msg);
        }
        if (delivered) {
            // Deliberately every message and deliberately not throttled: at a too-low cap the reply
            // is cut off mid-JSON, so skills silently do nothing while everything else looks fine.
            // It is a standing misconfiguration, and the nagging stops the moment it is corrected.
            if (LlmConfig.maxTokensTooLow()) {
                return List.of(Component.literal(String.format(
                        "⚠ llm.maxTokens is %d — too low. Long skill commands (farming) get cut off "
                                + "mid-reply and nothing runs. Set it to %d or more in /companion config.",
                        LlmConfig.maxTokens, LlmConfig.MIN_USEFUL_MAX_TOKENS)).withStyle(ChatFormatting.RED));
            }
            return List.of();
        }
        // Silence here is otherwise indistinguishable from the model being down: the message is
        // logged on arrival and then simply never acted on.
        LOGGER.warn("ConversationManager: message from {} reached no companion "
                        + "({} in queueData) — {}",
                msg.userName(), queueData.size(),
                diagnostics.length() == 0 ? "queueData is empty" : diagnostics.toString());
        if (target == null || targetDistance == Float.MAX_VALUE) {
            return List.of(); // nothing to point at: no companion, or it is not in this world
        }
        // Names whoever the speaker meant — the one they called for, not whoever is closest.
        Optional<String> earshot = outOfEarshotNotice(msg.userName(), target.getName(), targetDistance);
        if (earshot.isEmpty()) {
            return List.of();
        }
        return List.of(Component.literal(earshot.get()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Hand a group-addressed line to every companion in earshot.
     *
     * @return the lines to show the speaker, or null if this was not a broadcast at all — in which
     *         case the caller carries on with ordinary single-companion routing.
     */
    private static List<Component> deliverBroadcast(UserMessage msg, StringBuilder diagnostics) {
        String body = null;
        for (String keyword : BROADCAST_NAMES) {
            body = stripAddressedName(msg.message(), keyword, true);
            if (body != null) {
                break;
            }
        }
        if (body == null) {
            return null;
        }

        // A bare "all" with nothing after it is stripAddressedName's "delivered whole" case, which for
        // a keyword is just the word itself — an instruction to nobody. Not worth a turn from every
        // companion on the roster.
        if (BROADCAST_NAMES.contains(body.strip().toLowerCase(java.util.Locale.ROOT))) {
            return List.of(Component.literal(
                    "(nothing to pass on — say what you want them all to do, e.g. \"all: follow me\")")
                    .withStyle(ChatFormatting.GRAY));
        }

        // "all" means all of YOURS. Fanning a broadcast out to every companion in range would be the
        // most expensive form of the ownership hole: one line, one reply billed to each owner it
        // reached, and a stranger's sentence learned into every one of their corpora.
        List<AgentConversationData> heard = queueData.values().stream()
                .filter(data -> mayAddress(data, msg.speakerUuid()))
                .filter(data -> isCloseToPlayer(data, msg.userName()))
                .toList();
        if (heard.isEmpty()) {
            LOGGER.warn("ConversationManager: broadcast from {} reached no companion ({} in queueData) — {}",
                    msg.userName(), queueData.size(),
                    diagnostics.length() == 0 ? "queueData is empty" : diagnostics.toString());
            return List.of(Component.literal("(no companions close enough to hear you — /companion come)")
                    .withStyle(ChatFormatting.GRAY));
        }

        for (AgentConversationData data : heard) {
            data.onEvent(new UserMessage(body, msg.userName(), true, msg.speakerUuid()));
        }
        LOGGER.info("ConversationManager: broadcast from {} delivered to {}", msg.userName(),
                heard.stream().map(AgentConversationData::getName).collect(Collectors.joining(", ")));
        // Worth saying out loud: this is the one form that costs a reply per companion, and from the
        // speaker's seat a broadcast and a normal line look identical until several answers arrive.
        return List.of(Component.literal(String.format("(passed to %s)",
                heard.stream().map(AgentConversationData::getName).collect(Collectors.joining(", "))))
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * If {@code message} opens by addressing {@code name}, return what is left after the name;
     * otherwise return null.
     *
     * <p>Case-insensitive, and the name must be followed by the end of the line, whitespace, or a
     * comma/colon — so "Ava, go and mine" and "ava go and mine" both address Ava while "Avalanche
     * incoming" addresses nobody. Deliberately prefix-only: matching a name anywhere in the sentence
     * would route "tell Ava I said hello" to Ava, which is the opposite of what was asked.
     *
     * <p>A bare name with nothing after it ("Ava") is delivered whole — calling someone's name is a
     * complete thing to say, and handing the model an empty string is not.
     */
    private static String stripAddressedName(String message, String name) {
        return stripAddressedName(message, name, false);
    }

    /**
     * @param requireSeparator when true, only a comma or colon counts as the boundary — plain
     *        whitespace does not. Names do not need this: "Ava" rarely opens a sentence that is not
     *        aimed at Ava. The broadcast keywords very much do, because they are ordinary English
     *        openers — "all good", "all done", "everyone dies eventually" would otherwise each fan out
     *        to the whole roster and buy a reply from every companion. Requiring "all:" or "all," costs
     *        one character and matches how people write it anyway.
     */
    private static String stripAddressedName(String message, String name, boolean requireSeparator) {
        if (message == null || name == null || name.isBlank()) {
            return null;
        }
        String trimmed = message.strip();
        if (trimmed.length() < name.length()
                || !trimmed.regionMatches(true, 0, name, 0, name.length())) {
            return null;
        }
        String rest = trimmed.substring(name.length());
        if (rest.isEmpty()) {
            return trimmed;
        }
        char boundary = rest.charAt(0);
        if (boundary != ',' && boundary != ':'
                // java.lang.Character spelled out: this file imports adris.altoclef.player2api.Character.
                && (requireSeparator || !java.lang.Character.isWhitespace(boundary))) {
            return null;
        }
        String body = rest.substring(1).strip();
        return body.isEmpty() ? trimmed : body;
    }

    /**
     * How long a speaker goes between "can't hear you" notices, per player.
     *
     * <p>The notice fires on any line nobody heard, which on a busy server is every line two players
     * say to each other while the companion is away. Once is informative; once per line is noise.
     */
    private static final long EARSHOT_NOTICE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private static final ConcurrentHashMap<String, Long> lastEarshotNotice = new ConcurrentHashMap<>();

    private static Optional<String> outOfEarshotNotice(String userName, String companionName, float distance) {
        long now = System.nanoTime();
        Long last = lastEarshotNotice.get(userName);
        if (last != null && now - last < EARSHOT_NOTICE_INTERVAL_NANOS) {
            return Optional.empty();
        }
        lastEarshotNotice.put(userName, now);
        return Optional.of(String.format("(%s is %d blocks away and can't hear you — /companion come)",
                companionName, Math.round(distance)));
    }

    // register when an AI character messages
    public static void onAICharacterMessage(Event.CharacterMessage msg, UUID senderId) {
        if (!BehaviorConfig.aiCrossTalk) {
            return; // companions do not overhear each other — see BehaviorConfig#aiCrossTalk
        }
        UUID sendingUUID = msg.sendingCharacterData().getUUID();
        // Only between companions with the SAME owner, unless the operator has opened chat up. Every
        // forwarded line is a full turn on the receiving companion, billed to its owner, and each
        // reply prompts another — so across owners this is one player's companion spending another
        // player's tokens, indefinitely and with nobody typing. The javadoc on
        // BehaviorConfig#aiCrossTalk records a session that logged 382 of these with a single owner.
        getCloseDataByUUID(sendingUUID)
                .filter(data -> !(data.getUUID().equals(senderId)))
                .filter(data -> sharesOwner(data, msg.sendingCharacterData()))
                .forEach(data -> {
                    LOGGER.info("onCharMsg/ msg={}, sender={}, running onCharMsg for ={}", msg.message(), senderId,
                            data.getName());
                    data.onAICharacterMessage(msg);
                });
    }

    /**
     * Whether two companions belong to the same player, for the cross-talk fan-out.
     *
     * <p>Separate from {@link #mayAddress} because the question is different: that one asks whether a
     * <em>person</em> may drive a companion, this asks whether two companions are part of the same
     * household. Two ownerless companions are not treated as a household — no owner is not an
     * identity they share.
     */
    private static boolean sharesOwner(AgentConversationData listener, AgentConversationData speaker) {
        if (ServerPolicy.companionsAnswerAnyone) {
            return true;
        }
        if (listener == null || speaker == null
                || listener.getMod() == null || speaker.getMod() == null) {
            return false;
        }
        UUID speakerOwner = speaker.getMod().getOwnerUuid();
        return speakerOwner != null && listener.getMod().isOwner(speakerOwner);
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
     * How long work must sit undispatched before it counts as a stall.
     *
     * <p>This warning used to fire after 5 seconds, which caught the companion simply <em>talking</em>:
     * a turn is an LLM round-trip plus however long the TTS clip runs, and 7–11 second holds are
     * ordinary. Six of the seven warnings in the 2026-07-28 session were `tts=true` — designed
     * behaviour logged at WARN, which is how a warning stops meaning anything. A real stall is a lock
     * nobody releases, and that outlasts any sentence.
     */
    private static final long STALL_THRESHOLD_NANOS = TimeUnit.SECONDS.toNanos(15);

    /**
     * Warn when messages are queued but nothing is dispatching them. Only fires if work is actually
     * pending, has been pending longer than {@link #STALL_THRESHOLD_NANOS}, and at most once every 5
     * seconds — so a healthy server stays quiet whether it is idle or mid-sentence.
     */
    private static void reportStallIfWorkPending(String reason) {
        boolean stalled = queueData.values().stream()
                .anyMatch(data -> data.hasPendingEvents() && data.nanosWaiting() > STALL_THRESHOLD_NANOS);
        if (!stalled) {
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

    /**
     * Dispatch as many ready conversations as there are free pool slots, longest-waiting first.
     *
     * <p>Was one conversation per tick, and — because the single {@code dataToProcess} was fed to
     * every free completer — the same one repeatedly. Every companion with queued work now gets a
     * turn as soon as a slot frees, instead of the busiest one holding the floor.
     */
    private static void process(Consumer<Event.CharacterMessage> onCharacterEvent, Consumer<String> onErrEvent) {
        List<AgentConversationData> ready = queueData.values().stream()
                .filter(data -> data.getPriority() != 0)
                .sorted(Comparator.comparingLong(AgentConversationData::getPriority).reversed())
                .toList();
        // Materialized before dispatching: a lazy filter would re-evaluate isAvailible() after the
        // previous hand-off had already claimed a slot.
        List<LLMCompleter> free = llmCompleters.stream()
                .filter(LLMCompleter::isAvailible)
                .toList();

        if (ready.isEmpty()) {
            reportStallIfWorkPending("no conversation had a non-zero priority");
            return;
        }
        if (free.isEmpty()) {
            reportStallIfWorkPending(String.format(
                    "all %d LLM pool slots are busy — raise llm.maxConcurrentRequests if this persists",
                    llmCompleters.size()));
            return;
        }

        int dispatched = Math.min(ready.size(), free.size());
        for (int i = 0; i < dispatched; i++) {
            ready.get(i).process(onCharacterEvent, onErrEvent, free.get(i));
        }
        if (ready.size() > dispatched) {
            LOGGER.debug("ConversationManager: {} conversation(s) waiting on a free pool slot",
                    ready.size() - dispatched);
        }
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

        // No global gate any more. A conversation excludes itself while its own turn is in flight or
        // its own voice is still going (see getPriority), which is all the exclusion that was ever
        // needed — the process-wide version additionally froze every other companion.
        process(onCharacterEvent, onErrEvent);

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