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
