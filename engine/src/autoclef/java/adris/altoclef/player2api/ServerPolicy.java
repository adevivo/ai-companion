package adris.altoclef.player2api;

/**
 * The settings an <b>operator</b> decides, as opposed to the ones a player decides for themselves.
 *
 * <h2>Why there are two kinds of setting now</h2>
 *
 * Everything in {@code aicompanion.json} used to be read by whoever loaded the file, which is fine
 * in singleplayer — one JVM, one file — and wrong the moment there is a dedicated server. Two
 * separate problems fall out of it:
 *
 * <ul>
 *   <li><b>The player cannot configure anything.</b> Identity, model, key and memory all came from
 *       the server's copy, so a player on someone else's server got that operator's companion,
 *       spending that operator's tokens. The config screen said so in red.</li>
 *   <li><b>The operator cannot enforce anything.</b> There was no cap on how many companions a
 *       player could spawn, no way to close the commands again, and nothing stopping one player's
 *       companion answering another player.</li>
 * </ul>
 *
 * The split is by <b>who pays and who is affected</b>. Anything that costs the operator ticks,
 * changes the shared world, or has to bind every player equally lives here and is read from the
 * {@code "server"} block. Anything a player pays for or that is purely their own experience —
 * {@code companions}, {@code llm}, {@code embeddings}, {@code memory}, {@code tts} — stays in the
 * client's own file and is loaded by the client's own JVM.
 *
 * <h2>These statics are the AUTHORITY, and only on the machine that owns them</h2>
 *
 * ⚠️ A connected client also runs {@code CompanionConfig.load()} against its own file, because that
 * is how it gets its own key and its own corpus. It must <b>not</b> apply the {@code "server"} block
 * from that file to these fields: the values would be its own wishes rather than the server's rules,
 * and every one of them would be a lie in a place that reads like a fact.
 *
 * <p>Nothing here is read client-side. The permission checks, the caps, the chat routing and the
 * history file all run on the server, so the client needs the server's policy only to <em>show</em>
 * it — which arrives over the wire as JSON and is rendered read-only, never applied here.
 *
 * @see BehaviorConfig for the gameplay settings that moved into the same block but kept their
 *      existing statics
 */
public final class ServerPolicy {
    private ServerPolicy() {}

    /**
     * How many companions one player may have out at once. 0 = unlimited.
     *
     * <p>Nothing enforced this before, because nothing needed to: the commands were op-only and the
     * roster was the de-facto cap. Both of those are gone — a player brings their own roster now —
     * so this is the only thing between a shared server and as many pathfinders as somebody feels
     * like spawning.
     *
     * <p>Two rather than one because having a second companion out is a real part of the mod, and
     * two rather than five because each one is a Baritone pathfinder on the server thread.
     */
    public static volatile int maxCompanionsPerPlayer =
            Integer.parseInt(resolve("aicompanion.server.maxCompanionsPerPlayer",
                    "AICOMPANION_SERVER_MAXCOMPANIONSPERPLAYER", "2"));

    /**
     * How many companions may exist on the whole server. 0 = unlimited.
     *
     * <p>The per-player cap does not bound the server: twenty players at two each is forty
     * pathfinders ticking, which the KB records as <i>"probably the real adoption blocker"</i>. This
     * is the crude half of that problem — a hard ceiling that refuses the spawn rather than letting
     * TPS decide. The other half is a pathfinding budget, which is real work and is not here yet;
     * deliberately no config key for it until something reads one.
     */
    public static volatile int globalCompanionCap =
            Integer.parseInt(resolve("aicompanion.server.globalCompanionCap",
                    "AICOMPANION_SERVER_GLOBALCOMPANIONCAP", "20"));

    /**
     * Whether ordinary players may use {@code /companion} at all.
     *
     * <p>The way back to op-only, for an operator who wants to trial the mod or shut it off after
     * trouble without uninstalling it. When false, the player-facing subcommands demand level 2
     * instead of level 0 — the same predicate, a different level, so there is one code path rather
     * than a second gate that can disagree with the first.
     */
    public static volatile boolean allowPlayerCommands =
            Boolean.parseBoolean(resolve("aicompanion.server.allowPlayerCommands",
                    "AICOMPANION_SERVER_ALLOWPLAYERCOMMANDS", "true"));

    /**
     * Whether a companion answers anybody who speaks near it, or only its owner.
     *
     * <p>⚠️ Default false, and the default is the security-relevant one. Chat routing used to be
     * pure proximity: any player within 64 blocks drove any companion. That is charming on a family
     * LAN and indefensible anywhere else, because the turn is billed to the <em>owner</em> — with
     * {@code llm.clientBrain} on it is billed to the owner's own machine — and because the sentence
     * a stranger typed is then extracted into the owner's corpus under the owner's name.
     *
     * <p>Set it true to get the old behaviour back on a server where everyone is trusted.
     */
    public static volatile boolean companionsAnswerAnyone =
            Boolean.parseBoolean(resolve("aicompanion.server.companionsAnswerAnyone",
                    "AICOMPANION_SERVER_COMPANIONSANSWERANYONE", "false"));

    /**
     * How many identities one client may announce.
     *
     * <p>A roster arrives from the client now, which makes it untrusted input. This is not about
     * disk or memory — the list is tiny — but about the {@code /companion spawn} suggestion list and
     * the per-player cap having a bounded thing to reason about.
     */
    public static volatile int maxRosterEntries =
            Integer.parseInt(resolve("aicompanion.server.maxRosterEntries",
                    "AICOMPANION_SERVER_MAXROSTERENTRIES", "16"));

    /**
     * Whether conversation history survives a restart.
     *
     * <p>Default on, and this pass deliberately does not flip it. History and the memory corpus
     * overlap, and history does the cross-session job worse: measured on 2026-08-20, every surviving
     * mention of a fact the player had stated two days earlier was an {@code [assistant]} line — the
     * companion's own paraphrase, re-read and repeated every turn after the player's original
     * statement aged out under the prompt budget. The grounding guard protects the store from
     * self-citation; nothing protects the history.
     *
     * <p>⚠️ It is a switch rather than a deletion because history was, at that moment, the only
     * thing holding the fact at all. Turning it off before confirming that extraction now lands the
     * same details in the corpus removes them from the system entirely — which is exactly what
     * deleting the file did.
     *
     * <p>Server-side by necessity, not by preference: {@code ConversationHistory} is owned by
     * {@code AIPersistantData} on the server and the file is written there, so the server is the
     * only side that can act on this. It sits in the server block for that reason and is documented
     * as such, rather than sitting in the player's file doing nothing.
     */
    public static volatile boolean persistHistory =
            Boolean.parseBoolean(resolve("aicompanion.server.persistHistory",
                    "AICOMPANION_SERVER_PERSISTHISTORY", "true"));

    /**
     * Whether a player's companions are put away while they are offline.
     *
     * <p>Default on. A companion is an entity in the world save, so without this one spawned months
     * ago is still standing there — drawn as default Steve to everyone else (skins are a client-side
     * asset), unable to answer anyone, counting against the server's cap, and, on a server where
     * everyone is an operator, deletable by whoever walks past. All of that was observed on
     * 2026-08-21 and none of it was chosen.
     *
     * <p>Parking writes the whole entity — inventory included — to disk and restores it on the
     * owner's next join. ⚠️ It never removes a companion it could not first write <em>and read
     * back</em>: see {@code CompanionParking}. Turn it off if you would rather companions stayed
     * put and accepted the clutter.
     */
    public static volatile boolean parkWhenOwnerOffline =
            Boolean.parseBoolean(resolve("aicompanion.server.parkWhenOwnerOffline",
                    "AICOMPANION_SERVER_PARKWHENOWNEROFFLINE", "true"));

    /**
     * Whether the server answers — and <b>pays</b> — when a player's own brain fails.
     *
     * <p>⚠️ Default false, and this one is about money rather than correctness.
     *
     * <p>{@code llm.clientBrain} exists so each player spends their own key. A client announces at
     * join that it can think, and from then on the server sends it turns. If that client then cannot
     * reach its model, the obvious kindness is to answer for it — and the obvious kindness bills the
     * operator for a guest's misconfiguration, on every turn, indefinitely, with nobody able to see
     * it happening.
     *
     * <p>Measured on 2026-08-21: a guest pointed at a llama.cpp that was not running produced
     * {@code ConnectException: Connection refused} seven times, and the server quietly answered all
     * of them against its own paid endpoint — 25,144 tokens. She saw sensible replies, so nothing
     * looked wrong; the operator saw a bill with no notification. That is precisely the adoption
     * blocker moving the brain to the client was meant to remove, coming back through the fallback.
     *
     * <p><b>A client that never announced is a different case and still gets answered.</b> A vanilla
     * client, or one with {@code clientBrain} off, was always the server's to think for — that is the
     * operator's own configuration, not somebody else's broken one. The distinction is exactly the
     * capability handshake the transport already tracks.
     *
     * <p>Turn it on for a family LAN where covering for someone is the point.
     */
    public static volatile boolean serverAnswersWhenClientFails =
            Boolean.parseBoolean(resolve("aicompanion.server.serverAnswersWhenClientFails",
                    "AICOMPANION_SERVER_SERVERANSWERSWHENCLIENTFAILS", "false"));

    /** Whether {@code count} is within {@code cap}, treating 0 as unlimited. */
    public static boolean withinCap(int count, int cap) {
        return cap <= 0 || count < cap;
    }

    /** One line for the boot summary, so the enforced rules are visible without reading a file. */
    public static String describe() {
        return String.format(
                "perPlayer=%s, global=%s, playerCommands=%s, answerAnyone=%s, persistHistory=%s, "
                        + "parkOffline=%s",
                maxCompanionsPerPlayer <= 0 ? "unlimited" : maxCompanionsPerPlayer,
                globalCompanionCap <= 0 ? "unlimited" : globalCompanionCap,
                allowPlayerCommands, companionsAnswerAnyone, persistHistory, parkWhenOwnerOffline);
    }

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
