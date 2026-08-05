package com.neovetta.aicompanion.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compact health/hunger panel for every companion reporting in, drawn top-right.
 *
 * <p>Fed by the same {@link com.neovetta.aicompanion.AiCompanion#RADAR_UPDATE} snapshot the radar uses,
 * so it works past entity-tracking range and costs no extra traffic. Same threading contract as
 * {@link CompanionRadarHud}: written from netty, read on the render thread, each snapshot immutable and
 * swapped in whole so a reader sees one version or the other and never a mix.
 *
 * <p>Top-right is deliberate. The player's own health and hunger flank the hotbar and the radar sits
 * just above it, so anything bottom-aligned would collide; the top-left corner is taken by the token
 * HUD, which this was landing underneath. Top-right is empty in vanilla apart from status effect icons,
 * which sit lower and are transient.
 *
 * <p>Rows are one line each so the panel scales with the roster instead of with the screen — the thing
 * that ruled out mirroring vanilla's heart and drumstick rows, which run ~180px per companion.
 */
public final class CompanionStatusHud {

    private CompanionStatusHud() {}

    /**
     * Panel visibility.
     *
     * <p>{@code AUTO} is the default and shows the panel only when something needs attention — a
     * companion hurt or hungry — so it stays out of the way when everything is fine. {@code ON} pins it
     * open, {@code OFF} hides it entirely.
     */
    public enum Mode { AUTO, ON, OFF }

    private static volatile Mode mode = Mode.AUTO;

    /** One companion's last reported vitals. */
    private record Snapshot(String name, Identifier worldId, float health, float maxHealth,
                            int food, float saturation, long receivedAtMs) {}

    /** entityId → last snapshot. Written from netty, read from the render thread. */
    private static final Map<Integer, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    /**
     * While AUTO, keep the panel up until this timestamp even if everything has recovered.
     *
     * <p>Without the linger it vanishes the instant a companion crosses back over the threshold, which
     * in practice means it blinks in and out during a fight — regeneration and a bite of food both move
     * the numbers back and forth across the line several times a minute.
     */
    private static volatile long autoShowUntilMs;

    /** Store a fresh snapshot (called from the packet receiver). */
    public static void update(int entityId, String name, Identifier worldId,
                              float health, float maxHealth, int food, float saturation) {
        SNAPSHOTS.put(entityId, new Snapshot(name, worldId, health, maxHealth, food, saturation,
                System.currentTimeMillis()));
    }

    /** Forget every companion. Called on disconnect, for the same reason the radar does it. */
    public static void clear() {
        SNAPSHOTS.clear();
        autoShowUntilMs = 0L;
    }

    /** Advance AUTO → ON → OFF → AUTO and return the new mode (for the chat echo). */
    public static Mode cycleMode() {
        Mode next = switch (mode) {
            case AUTO -> Mode.ON;
            case ON -> Mode.OFF;
            case OFF -> Mode.AUTO;
        };
        mode = next;
        return next;
    }

    public static Mode mode() {
        return mode;
    }

    // Layout.
    private static final int MARGIN_X = 6;
    private static final int MARGIN_Y = 6;
    private static final int ROW_HEIGHT = 11;
    private static final int BAR_WIDTH = 40;
    private static final int BAR_HEIGHT = 5;
    private static final int NAME_MAX_WIDTH = 60;
    private static final int GAP = 4;
    private static final int NUMBER_WIDTH = 14;

    // Thresholds that make AUTO show something.
    private static final float HEALTH_SHOW_BELOW = 0.70f;
    private static final int FOOD_SHOW_BELOW = 18;
    private static final long AUTO_LINGER_MS = 5_000L;

    private static final long STALE_MS = 5_000L;
    private static final long GIVE_UP_MS = 60_000L;

    // Colors.
    private static final int COLOR_TRACK = 0x50000000;
    private static final int COLOR_BAR_BG = 0x40FFFFFF;
    private static final int COLOR_HEALTH_OK = 0xFF55DD55;
    private static final int COLOR_HEALTH_MID = 0xFFFFAA33;
    private static final int COLOR_HEALTH_LOW = 0xFFFF5555;
    private static final int COLOR_FOOD_OK = 0xFFCC8844;
    private static final int COLOR_FOOD_LOW = 0xFFFFAA33;
    private static final int COLOR_SATURATION = 0x66FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xAAAAAA;

    /** Render callback body — registered against {@code HudRenderCallback.EVENT} in the client init. */
    public static void render(GuiGraphics ctx, float tickDelta) {
        if (mode == Mode.OFF || SNAPSHOTS.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }

        long now = System.currentTimeMillis();
        Identifier here = client.world.getRegistryKey().getValue();

        List<Row> rows = new ArrayList<>();
        boolean anyNeedsAttention = false;
        for (Map.Entry<Integer, Snapshot> entry : SNAPSHOTS.entrySet()) {
            Snapshot snap = entry.getValue();
            long age = now - snap.receivedAtMs();
            if (age > GIVE_UP_MS) {
                SNAPSHOTS.remove(entry.getKey()); // despawned, or long gone — stop tracking it
                continue;
            }
            boolean crossDim = snap.worldId() == null || !here.equals(snap.worldId());
            boolean stale = age > STALE_MS;
            float frac = snap.maxHealth() > 0f ? snap.health() / snap.maxHealth() : 1f;
            if (frac < HEALTH_SHOW_BELOW || snap.food() < FOOD_SHOW_BELOW) {
                anyNeedsAttention = true;
            }
            rows.add(new Row(snap, frac, crossDim, stale));
        }
        if (rows.isEmpty()) {
            return;
        }

        if (mode == Mode.AUTO) {
            if (anyNeedsAttention) {
                autoShowUntilMs = now + AUTO_LINGER_MS;
            } else if (now > autoShowUntilMs) {
                return;
            }
        }

        // Stable ordering, so rows do not swap places under the cursor as packets arrive.
        rows.sort(Comparator.comparing(r -> r.snapshot().name()));

        TextRenderer tr = client.textRenderer;
        int nameWidth = 0;
        for (Row r : rows) {
            nameWidth = Math.max(nameWidth, tr.getWidth(r.snapshot().name()));
        }
        nameWidth = Math.min(nameWidth, NAME_MAX_WIDTH);

        // Right-aligned: the token HUD owns the top-left corner (PANEL_X/Y = 4) and this was landing
        // underneath it. Lay the columns out left-to-right as before, then shift the whole block so its
        // right edge sits a margin in from the screen edge — that way the bars stay aligned with each
        // other however long the names are, instead of ragged against the right edge.
        int panelWidth = nameWidth + GAP + BAR_WIDTH + GAP + NUMBER_WIDTH + GAP
                + BAR_WIDTH + GAP + NUMBER_WIDTH;
        int panelLeft = ctx.getScaledWindowWidth() - MARGIN_X - panelWidth;

        int healthBarX = panelLeft + nameWidth + GAP;
        int healthNumX = healthBarX + BAR_WIDTH + GAP;
        int foodBarX = healthNumX + NUMBER_WIDTH + GAP;
        int foodNumX = foodBarX + BAR_WIDTH + GAP;
        int panelRight = foodNumX + NUMBER_WIDTH;

        // One backing plate behind the whole panel rather than per row — cheaper, and it reads as a
        // single element instead of a stack of unrelated strips.
        ctx.fill(panelLeft - 3, MARGIN_Y - 3, panelRight + 2,
                MARGIN_Y + rows.size() * ROW_HEIGHT, COLOR_TRACK);

        int y = MARGIN_Y;
        for (Row r : rows) {
            Snapshot snap = r.snapshot();
            boolean dim = r.stale() || r.crossDim();
            int textColor = dim ? COLOR_TEXT_DIM : COLOR_TEXT;
            int barTop = y + 1;

            String name = snap.name();
            while (tr.getWidth(name) > NAME_MAX_WIDTH && name.length() > 1) {
                name = name.substring(0, name.length() - 1);
            }
            ctx.drawShadowedText(tr, Text.literal(name), panelLeft, y, textColor);

            int healthColor = r.healthFraction() > 0.6f ? COLOR_HEALTH_OK
                    : r.healthFraction() > 0.3f ? COLOR_HEALTH_MID : COLOR_HEALTH_LOW;
            drawBar(ctx, healthBarX, barTop, r.healthFraction(), healthColor, dim);
            ctx.drawShadowedText(tr, Text.literal(String.valueOf(Math.round(snap.health()))),
                    healthNumX, y, textColor);

            float foodFrac = Math.min(1f, snap.food() / 20f);
            int foodColor = snap.food() >= FOOD_SHOW_BELOW ? COLOR_FOOD_OK : COLOR_FOOD_LOW;
            drawBar(ctx, foodBarX, barTop, foodFrac, foodColor, dim);
            // Saturation as a thin overlay on the hunger bar: it is the buffer that gets spent before
            // food moves at all, so watching it drain is the only way to see healing costing anything.
            float satFrac = Math.min(1f, snap.saturation() / 20f);
            if (satFrac > 0f) {
                int satW = Math.max(1, Math.round(BAR_WIDTH * satFrac));
                ctx.fill(foodBarX, barTop, foodBarX + satW, barTop + 1, COLOR_SATURATION);
            }
            ctx.drawShadowedText(tr, Text.literal(String.valueOf(snap.food())), foodNumX, y, textColor);

            // Marker to the left of the name, not past the right edge — that would hang off screen
            // now the panel is right-aligned.
            if (r.crossDim()) {
                ctx.drawShadowedText(tr, Text.literal("↗"), panelLeft - 10, y, COLOR_TEXT_DIM);
            } else if (r.stale()) {
                ctx.drawShadowedText(tr, Text.literal("?"), panelLeft - 10, y, COLOR_TEXT_DIM);
            }

            y += ROW_HEIGHT;
        }
    }

    /** A track plus a proportional fill, dimmed when the reading is stale or cross-dimension. */
    private static void drawBar(GuiGraphics ctx, int x, int y, float fraction, int color, boolean dim) {
        ctx.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BAR_BG);
        float clamped = Math.max(0f, Math.min(1f, fraction));
        int filled = Math.round(BAR_WIDTH * clamped);
        if (filled > 0) {
            // Halve the alpha rather than the colour so a dimmed bar still reads as the same hue.
            int shown = dim ? (color & 0x00FFFFFF) | 0x60000000 : color;
            ctx.fill(x, y, x + filled, y + BAR_HEIGHT, shown);
        }
    }

    /** A snapshot prepared for drawing. */
    private record Row(Snapshot snapshot, float healthFraction, boolean crossDim, boolean stale) {}
}
