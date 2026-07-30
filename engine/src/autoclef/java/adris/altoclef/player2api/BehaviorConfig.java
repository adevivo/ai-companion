package adris.altoclef.player2api;

/**
 * Conversation-gating configuration for the AI Companion fork. These knobs bound how often chat
 * reaches the LLM, which is the only thing standing between ambient chatter and a bill on a paid
 * endpoint. Fed from the consumer mod's {@code behavior.*} config block; overridable via system
 * property or environment variable like {@link LlmConfig}.
 */
public final class BehaviorConfig {
    private BehaviorConfig() {}

    /**
     * When non-blank, only chat messages starting with this prefix are routed to the companion; the
     * prefix is stripped before the model sees it. Blank (the default) means the companion responds to
     * all nearby chat, which is what you want in singleplayer. Set it (e.g. {@code "@"}) when the
     * endpoint costs money or the world is shared.
     */
    public static volatile String triggerPrefix =
            resolve("aicompanion.behavior.triggerPrefix", "AICOMPANION_BEHAVIOR_TRIGGERPREFIX", "");

    /**
     * Minimum seconds between LLM turns for a given companion ({@code <= 0} = no throttle). Messages
     * arriving inside the window are <em>not</em> dropped — they stay queued and are folded into the
     * next turn, since the event queue already batches. This caps request rate, not conversation.
     */
    public static volatile double thinkThrottleSeconds =
            Double.parseDouble(resolve("aicompanion.behavior.thinkThrottleSeconds",
                    "AICOMPANION_BEHAVIOR_THINKTHROTTLESECONDS", "0"));

    /**
     * Whether {@code build_structure} charges the companion's inventory for the blocks it places.
     * True (the default) keeps building honest in survival: the companion has to own the materials
     * first, and a build it cannot afford is refused rather than conjured. Set false to restore the
     * old creative-style behaviour where blocks appear out of nothing.
     */
    public static volatile boolean buildCostsMaterials =
            Boolean.parseBoolean(resolve("aicompanion.behavior.buildCostsMaterials",
                    "AICOMPANION_BEHAVIOR_BUILDCOSTSMATERIALS", "true"));

    /**
     * Whether {@code build_structure} checks a generated plan against the real terrain before placing
     * it. True by default: the model has no reliable sense of terrain height, and a wrong Y otherwise
     * costs real materials for a build nobody can see.
     *
     * <p>The check is deliberately one-sided. A plan that came out below the ground is lifted onto it
     * (up to {@code BuildPlacement.MAX_LIFT}), because buried is never what was asked for and is
     * invisible once it happens. A plan above the ground is left exactly as generated — "on top of
     * the ground" is a one-block gap, and towers and platforms are legitimately higher — and refused
     * only past {@code BuildPlacement.MAX_AIR_GAP}, where no plausible request could have put it.
     *
     * <p>Set false to disable the check entirely, e.g. for work deep underground.
     */
    public static volatile boolean buildGroundCheck =
            Boolean.parseBoolean(resolve("aicompanion.behavior.buildGroundCheck",
                    "AICOMPANION_BEHAVIOR_BUILDGROUNDCHECK", "true"));

    /**
     * How many turns in a row the companion may take on its own initiative before it must wait to be
     * spoken to ({@code <= 0} = unlimited).
     *
     * <p>Every finished command queues a "what shall we do next?" prompt, so one instruction can drive
     * an unbounded chain of LLM turns. Measured on a local endpoint: 17 calls for 8 user messages —
     * 9 turns, over half, were self-triggered, and they included four actions nobody asked for
     * (`attack spider`, `attack skeleton`, `food`, `farm`) issued after the owner said "good job".
     *
     * <p>Two is enough to finish a genuine multi-step plan — gather, then build — without letting the
     * companion wander off on its own for the rest of the session. The counter resets the moment
     * anybody talks to it.
     */
    public static volatile int maxAutonomousTurns =
            Integer.parseInt(resolve("aicompanion.behavior.maxAutonomousTurns",
                    "AICOMPANION_BEHAVIOR_MAXAUTONOMOUSTURNS", "2"));

    /**
     * Whether {@code build_structure} builds with its hands instead of conjuring the structure.
     *
     * <p>True by default. With it on, the companion walks to the site, stands somewhere it can actually
     * reach, and places blocks a few per tick with an arm swing and a placement sound, moving along as
     * each spot is exhausted. Blocks are still written directly rather than right-clicked — going through
     * real item use needs scaffolding and support faces and is a known source of stalls — so this is
     * about the build being physically situated and paced, not about simulating every placement rule.
     *
     * <p>Set false to restore the old behaviour: up to 256 blocks a tick, no reach check, no walking, from
     * anywhere on the map. That path is kept unchanged as a one-line escape hatch if paced building
     * misbehaves in a real world.
     */
    public static volatile boolean buildPhysicalPlacement =
            Boolean.parseBoolean(resolve("aicompanion.behavior.buildPhysicalPlacement",
                    "AICOMPANION_BEHAVIOR_BUILDPHYSICALPLACEMENT", "true"));

    /**
     * Blocks placed per tick when {@link #buildPhysicalPlacement} is on. Clamped to 1..64.
     *
     * <p>Two looks like someone working steadily. Raising it finishes large builds sooner at the cost of
     * blocks appearing in visible clumps; a build the owner cancels out of boredom is worse than one that
     * looks slightly too quick, so raise this rather than turning physical placement off.
     */
    public static volatile int buildBlocksPerTick =
            Integer.parseInt(resolve("aicompanion.behavior.buildBlocksPerTick",
                    "AICOMPANION_BEHAVIOR_BUILDBLOCKSPERTICK", "2"));

    /**
     * Whether hostile mobs treat the companion as they would a player.
     *
     * <p>True by default. The companion is a {@code LivingEntity}, not a {@code Player}, and vanilla
     * hostiles look for targets with an explicit {@code Player.class} filter — so with this off, mobs
     * walk straight past it and it is only ever attacked in retaliation for swinging first.
     *
     * <p>This also decides whether the defence behaviour is reachable at all: everything in
     * {@link adris.altoclef.chains.MobDefenseChain} hangs off {@code EntityTracker.getHostiles()},
     * which asks whether a mob is targeting the companion. Set this false and that list goes back to
     * being permanently empty.
     */
    public static volatile boolean mobsTargetCompanion =
            Boolean.parseBoolean(resolve("aicompanion.behavior.mobsTargetCompanion",
                    "AICOMPANION_BEHAVIOR_MOBSTARGETCOMPANION", "true"));

    /**
     * Whether the companion fights back against hostiles that are targeting it.
     *
     * <p>True by default. Note that this gates only the deliberate {@code KillEntitiesTask} response
     * in {@link adris.altoclef.chains.MobDefenseChain}; the opportunistic swing at whatever is already
     * in arm's reach (the kill aura) is separate and is not affected. Set false and the companion will
     * absorb hits without ever choosing to engage.
     */
    public static volatile boolean defenseFightBack =
            Boolean.parseBoolean(resolve("aicompanion.behavior.defenseFightBack",
                    "AICOMPANION_BEHAVIOR_DEFENSEFIGHTBACK", "true"));

    /**
     * Whether the companion raises a shield when threatened.
     *
     * <p>True by default. Gating this to false makes every shield-aware decision behave as though the
     * companion owns no shield at all, which also (correctly) lowers its estimate of what it can take
     * on. Known gap: shield durability is not yet wired up for a non-player entity, so a raised shield
     * currently never wears out — see {@code CompanionEntity#damageArmor} for the armour equivalent
     * that is wired.
     */
    public static volatile boolean defenseUseShield =
            Boolean.parseBoolean(resolve("aicompanion.behavior.defenseUseShield",
                    "AICOMPANION_BEHAVIOR_DEFENSEUSESHIELD", "true"));

    /**
     * Whether the companion may run away — from hostiles it judges it cannot beat, from incoming
     * arrows, and behind hastily-built projectile walls.
     *
     * <p>False by default, deliberately. All of this logic sits behind
     * {@code EntityTracker.getHostiles()}, which requires a mob to actually be targeting the companion;
     * before hostile mobs could target a non-player entity at all, that list was permanently empty and
     * none of this code had ever run in a real world. Turning it on at the same moment as mob aggro
     * would mean shipping two untested behaviours at once, and the failure mode is the companion
     * abandoning a farm or a build halfway through to sprint over the horizon.
     *
     * <p>With this off the companion stands its ground: it keeps working, and the kill aura plus
     * {@link #defenseFightBack} handle whatever reaches it. Turn it on once you have watched it get
     * mobbed and decided you want flight instead.
     */
    public static volatile boolean defenseFleeFromHostiles =
            Boolean.parseBoolean(resolve("aicompanion.behavior.defenseFleeFromHostiles",
                    "AICOMPANION_BEHAVIOR_DEFENSEFLEEFROMHOSTILES", "false"));

    /**
     * Apply {@link #triggerPrefix} to an incoming chat line. Returns the message the model should see
     * (prefix stripped, trimmed), or {@code null} if this message is not addressed to the companion.
     */
    public static String applyTriggerPrefix(String message) {
        String prefix = triggerPrefix;
        if (prefix == null || prefix.isBlank()) {
            return message;
        }
        if (message == null || !message.startsWith(prefix)) {
            return null;
        }
        String stripped = message.substring(prefix.length()).trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String resolve(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
