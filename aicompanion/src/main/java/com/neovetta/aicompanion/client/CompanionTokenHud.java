package com.neovetta.aicompanion.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.text.Text;

/**
 * Client-side panel showing this session's LLM token spend: running totals plus a per-minute burn-rate
 * graph. On a paid endpoint the only other view is the {@code llm.usageReportEveryTokens} chat
 * milestone, which by default fires once every 100k tokens — far too coarse to notice a companion
 * stuck in a think loop. This makes the rate visible at a glance.
 *
 * <p>Totals arrive as <em>cumulative</em> snapshots about once a second, and this class derives the
 * history by diffing consecutive ones into per-minute buckets. Cumulative-plus-diff means a dropped
 * update is free — the next one carries the missing tokens — and whoever is counting needs no history
 * buffer of its own.
 *
 * <h2>Whichever machine paid is the machine that reports</h2>
 *
 * The counters in {@code Player2APIService} are {@code static}, so they live in whichever JVM made the
 * call. That was the server without exception, and this panel was fed purely by
 * {@link com.neovetta.aicompanion.AiCompanion#TOKEN_USAGE}.
 *
 * <p>⚠️ {@code llm.clientBrain} broke that assumption without breaking the packet. With the brain on
 * the owning client the server's counters stay at zero for ever — and it went on pushing them every
 * second, each one <b>overwriting</b> the real figures this machine already had. Observed 2026-08-20:
 * 30 requests and 101,101 tokens spent client-side, against a panel reading zero the whole session.
 * That is worse than the panel vanishing, because a wrong number reads as a working feature.
 *
 * <p>So {@link #selfUpdate()} feeds the panel from this machine's own counters when this machine is
 * the one spending, and the server stays quiet when the owner is thinking client-side. Both halves are
 * independently correct, which is what the fallback needs: a turn the client could not answer is paid
 * for with the <em>server's</em> key, and that is genuinely the operator's bill and not the player's.
 *
 * <p>Threading follows {@link CompanionRadarHud}: the receiver writes from a netty thread and the HUD
 * callback reads on the render thread. The scalar fields are {@code volatile} with {@code receivedAtMs}
 * written last as a publication barrier; the bucket ring is only ever mutated inside
 * {@code synchronized} blocks because rolling it is a multi-step read-modify-write, unlike the radar's
 * independent scalars.
 */
public final class CompanionTokenHud {

    private CompanionTokenHud() {}

    // Visibility, flipped by /companion tokens. Session-scoped like the radar's mode — no client
    // config file yet, so it returns to the default on restart. Default on: the panel is the only
    // fine-grained view of spend, and someone who has never seen it cannot know to turn it on.
    private static volatile boolean enabled = true;

    /** Flip the panel on/off and return the new state (for the chat echo). */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean enabled() {
        return enabled;
    }

    // Last snapshot, from whichever side is counting. receivedAtMs == 0 means "never received".
    private static volatile long promptTokens, completionTokens, totalTokens;
    private static volatile int requests;
    private static volatile long receivedAtMs = 0L;

    /** Client ticks since the last self-update, so this samples at the server packet's ~1s cadence. */
    private static int selfTickCounter = 0;

    /**
     * Feed the panel from THIS machine's counters, when this machine is the one spending.
     *
     * <p>Called every client tick and samples once a second, matching the cadence the panel's
     * per-minute buckets and its 60-second staleness cutoff were both built around. Pushing only at
     * the end of a turn would let the panel time out and disappear during a quiet stretch, which is
     * exactly when someone is looking at it to see whether a companion is burning tokens unattended.
     *
     * <p>The {@code requests <= 0} guard is what keeps this from fighting the server. A client that
     * has spent nothing has nothing to say — on a server-side brain that is every client, and the
     * packet remains the only source, untouched.
     */
    public static void selfUpdate() {
        if (++selfTickCounter % 20 != 0) {
            return;
        }
        adris.altoclef.player2api.Player2APIService.UsageSnapshot usage =
                adris.altoclef.player2api.Player2APIService.usageSnapshot();
        if (usage.requests() <= 0) {
            return;
        }
        update(usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage.requests());
    }

    // Per-minute history. buckets[head] is the in-progress minute; older minutes walk backwards,
    // wrapping. Guarded by BUCKETS — never read or written outside a synchronized block.
    private static final int BUCKET_COUNT = 30;
    private static final long BUCKET_MS = 60_000L;
    private static final long[] BUCKETS = new long[BUCKET_COUNT];
    private static int head = 0;
    private static long bucketStartMs = 0L;
    private static long lastTotal = 0L;

    /** Store a fresh snapshot and fold its delta into the current minute (called from the receiver). */
    public static void update(long promptTokens, long completionTokens, long totalTokens, int requests) {
        long now = System.currentTimeMillis();
        synchronized (BUCKETS) {
            if (bucketStartMs == 0L) {
                bucketStartMs = now; // first packet — start the clock, don't bank a delta
            } else if (totalTokens < lastTotal) {
                // Counters went backwards: the world was reloaded and Player2APIService reset its
                // session totals. Start over rather than banking a negative or a bogus spike.
                java.util.Arrays.fill(BUCKETS, 0L);
                head = 0;
                bucketStartMs = now;
            } else {
                long elapsedMinutes = (now - bucketStartMs) / BUCKET_MS;
                if (elapsedMinutes > 0) {
                    // Advance the ring, zeroing every minute we skipped so an idle gap reads as a
                    // trough instead of leaving stale bars in place.
                    long steps = Math.min(elapsedMinutes, BUCKET_COUNT);
                    for (long i = 0; i < steps; i++) {
                        head = (head + 1) % BUCKET_COUNT;
                        BUCKETS[head] = 0L;
                    }
                    bucketStartMs += elapsedMinutes * BUCKET_MS;
                }
                BUCKETS[head] += totalTokens - lastTotal;
            }
            lastTotal = totalTokens;
        }
        CompanionTokenHud.promptTokens = promptTokens;
        CompanionTokenHud.completionTokens = completionTokens;
        CompanionTokenHud.totalTokens = totalTokens;
        CompanionTokenHud.requests = requests;
        CompanionTokenHud.receivedAtMs = now; // set last: implies the rest are written
    }

    // Layout + color constants. Top-left: status effects own the top-right, and the hotbar/XP bar plus
    // the radar own the bottom.
    private static final int PANEL_X = 4;
    private static final int PANEL_Y = 4;
    private static final int PANEL_WIDTH = 122;
    private static final int LINE_HEIGHT = 10;
    private static final int GRAPH_HEIGHT = 24;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_GAP = 1;
    /** Stop drawing once the companion has been silent this long — the same give-up window as the radar. */
    private static final long GIVE_UP_MS = 60_000L;
    private static final int COLOR_PANEL = 0x80000000;
    private static final int COLOR_LABEL = 0xAAAAAA;
    private static final int COLOR_VALUE = 0xFFFFFF;
    private static final int COLOR_IN = 0x77CCFF;
    private static final int COLOR_OUT = 0xFFCC66;
    private static final int COLOR_BAR = 0xFF77CCFF;
    private static final int COLOR_BAR_PARTIAL = 0xFF3E6E8A;
    private static final int COLOR_GRAPH_BG = 0x30FFFFFF;

    /** Render callback body — registered against {@code HudRenderCallback.EVENT} in the client init. */
    public static void render(GuiGraphics ctx, float tickDelta) {
        if (!enabled) {
            // Only drawing stops — update() keeps banking deltas, so switching the panel back on
            // shows the real history for the time it was hidden instead of a hole in the graph.
            return;
        }
        if (receivedAtMs == 0L) {
            return; // no companion has ever reported in this session
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        if (System.currentTimeMillis() - receivedAtMs > GIVE_UP_MS) {
            return; // companion despawned or stopped ticking — the panel is only for a live one
        }
        if (client.options.debugEnabled) {
            return; // F3 overlay owns the top-left; don't draw on top of it
        }

        // Snapshot the ring under the lock so the peak we scale by matches the bars we draw.
        long[] history = new long[BUCKET_COUNT];
        int localHead;
        synchronized (BUCKETS) {
            System.arraycopy(BUCKETS, 0, history, 0, BUCKET_COUNT);
            localHead = head;
        }
        long peak = 0L;
        for (long v : history) {
            peak = Math.max(peak, v);
        }

        TextRenderer tr = client.textRenderer;
        int graphY = PANEL_Y + 3 + LINE_HEIGHT * 2;
        int panelBottom = graphY + GRAPH_HEIGHT + 2 + LINE_HEIGHT + 2;
        ctx.fill(PANEL_X - 2, PANEL_Y - 2, PANEL_X + PANEL_WIDTH + 2, panelBottom, COLOR_PANEL);

        // Line 1: total + request count.
        ctx.drawShadowedText(tr, Text.literal("tokens"), PANEL_X, PANEL_Y, COLOR_LABEL);
        ctx.drawShadowedText(tr, Text.literal(abbreviate(totalTokens)), PANEL_X + 34, PANEL_Y, COLOR_VALUE);
        String reqs = requests + " req";
        ctx.drawShadowedText(tr, Text.literal(reqs),
                PANEL_X + PANEL_WIDTH - tr.getWidth(reqs), PANEL_Y, COLOR_LABEL);

        // Line 2: the in/out split.
        String in = "in " + abbreviate(promptTokens);
        ctx.drawShadowedText(tr, Text.literal(in), PANEL_X, PANEL_Y + LINE_HEIGHT, COLOR_IN);
        ctx.drawShadowedText(tr, Text.literal("out " + abbreviate(completionTokens)),
                PANEL_X + tr.getWidth(in) + 6, PANEL_Y + LINE_HEIGHT, COLOR_OUT);

        // The graph: BUCKET_COUNT minutes, oldest at the left edge, the in-progress minute at the right.
        ctx.fill(PANEL_X, graphY, PANEL_X + PANEL_WIDTH, graphY + GRAPH_HEIGHT, COLOR_GRAPH_BG);
        if (peak > 0) {
            for (int i = 0; i < BUCKET_COUNT; i++) {
                // i = 0 is the oldest minute; walking forward from head+1 wraps to it.
                long value = history[(localHead + 1 + i) % BUCKET_COUNT];
                if (value <= 0) {
                    continue;
                }
                int barHeight = Math.max(1, (int) Math.round((double) value / peak * GRAPH_HEIGHT));
                int barLeft = PANEL_X + i * (BAR_WIDTH + BAR_GAP);
                // The newest bucket is a partial minute; dim it so a half-filled bar doesn't read as
                // a real drop in usage.
                boolean partial = i == BUCKET_COUNT - 1;
                ctx.fill(barLeft, graphY + GRAPH_HEIGHT - barHeight, barLeft + BAR_WIDTH,
                        graphY + GRAPH_HEIGHT, partial ? COLOR_BAR_PARTIAL : COLOR_BAR);
            }
        }

        // Line 3: the graph's units and scale.
        int labelY = graphY + GRAPH_HEIGHT + 2;
        ctx.drawShadowedText(tr, Text.literal("tok/min"), PANEL_X, labelY, COLOR_LABEL);
        String peakLabel = "peak " + abbreviate(peak);
        ctx.drawShadowedText(tr, Text.literal(peakLabel),
                PANEL_X + PANEL_WIDTH - tr.getWidth(peakLabel), labelY, COLOR_LABEL);
    }

    /**
     * Grouped digits up to 9,999, then one decimal of k/M — the panel is only ~120px wide and a raw
     * "1,482,905" would push the right-aligned labels into the left-aligned ones.
     */
    private static String abbreviate(long n) {
        if (n < 10_000L) {
            return String.format("%,d", n);
        }
        if (n < 1_000_000L) {
            return String.format("%.1fk", n / 1_000.0);
        }
        return String.format("%.1fM", n / 1_000_000.0);
    }
}
