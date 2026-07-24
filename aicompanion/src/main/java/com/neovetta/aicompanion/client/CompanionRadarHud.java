package com.neovetta.aicompanion.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Client-side locator bar that points toward the companion so the owner can walk to it without
 * recalling. Works past entity-tracking range because the server pushes coordinates
 * ({@link com.neovetta.aicompanion.AiCompanion#RADAR_UPDATE}); this class just holds the last snapshot
 * and renders it.
 *
 * <p>State is static and session-scoped (mode is not persisted — not worth a client config file for
 * v1). The receiver writes the snapshot from a netty thread and the HUD callback reads it on the
 * render thread; fields are {@code volatile} and a torn read (new position, stale dimension) self-heals
 * on the next packet ~100ms later, which is acceptable for a cosmetic bar.
 */
public final class CompanionRadarHud {

    private CompanionRadarHud() {}

    /** Radar visibility. AUTO hides the bar when the companion is close and not stale/cross-dimension. */
    public enum Mode { AUTO, ON, OFF }

    // Default ON: the bar is always visible whenever a companion is reporting in. AUTO (auto-hide when
    // close) and OFF remain reachable via /companion radar or the keybind.
    private static volatile Mode mode = Mode.ON;

    // Last snapshot from the server. receivedAtMs == 0 means "never received".
    private static volatile double x, y, z;
    private static volatile Identifier worldId;
    private static volatile float health, maxHealth;
    private static volatile long receivedAtMs = 0L;

    /** Store a fresh snapshot (called from the packet receiver). */
    public static void update(double x, double y, double z, Identifier worldId,
                              float health, float maxHealth) {
        CompanionRadarHud.x = x;
        CompanionRadarHud.y = y;
        CompanionRadarHud.z = z;
        CompanionRadarHud.worldId = worldId;
        CompanionRadarHud.health = health;
        CompanionRadarHud.maxHealth = maxHealth;
        CompanionRadarHud.receivedAtMs = System.currentTimeMillis(); // set last: implies the rest are written
    }

    /** Advance AUTO → ON → OFF → AUTO and return the new mode (for the chat echo / keybind). */
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

    // Layout + color constants.
    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_BOTTOM_OFFSET = 48; // px above the screen bottom (clears hotbar + XP bar)
    private static final long STALE_MS = 5_000L;
    private static final long GIVE_UP_MS = 60_000L;
    private static final int COLOR_MARKER = 0xFFFFFFFF;
    private static final int COLOR_MARKER_DIM = 0xFFBBBBBB;
    private static final int COLOR_MARKER_LOW = 0xFFFF5555;

    /** Render callback body — registered against {@code HudRenderCallback.EVENT} in the client init. */
    public static void render(GuiGraphics ctx, float tickDelta) {
        if (mode == Mode.OFF || receivedAtMs == 0L) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return;
        }
        long age = System.currentTimeMillis() - receivedAtMs;
        if (age > GIVE_UP_MS) {
            return; // gone too long — stop drawing entirely
        }

        boolean crossDim = worldId == null
                || !client.world.getRegistryKey().getValue().equals(worldId);
        boolean stale = age > STALE_MS;
        double dx = x - player.getX();
        double dy = y - player.getY();
        double dz = z - player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // AUTO shows only when it's actually useful: far away, stale, or another dimension.
        if (mode == Mode.AUTO && !crossDim && !stale && dist <= 16.0) {
            return;
        }

        boolean dim = stale || crossDim;
        int screenW = ctx.getScaledWindowWidth();
        int screenH = ctx.getScaledWindowHeight();
        int centerX = screenW / 2;
        int barLeft = centerX - BAR_WIDTH / 2;
        int barRight = barLeft + BAR_WIDTH;
        int barY = screenH - BAR_BOTTOM_OFFSET;
        TextRenderer tr = client.textRenderer;

        // Bar background + border + center tick.
        ctx.fill(barLeft - 1, barY - 1, barRight + 1, barY + BAR_HEIGHT + 1, (dim ? 0x40 : 0x80) << 24);
        ctx.fill(barLeft, barY, barRight, barY + BAR_HEIGHT, dim ? 0x20FFFFFF : 0x40FFFFFF);
        ctx.fill(centerX, barY - 2, centerX + 1, barY + BAR_HEIGHT + 2, dim ? 0x60FFFFFF : 0xA0FFFFFF);

        if (crossDim) {
            ctx.drawCenteredShadowedText(tr, Text.literal("other dimension"),
                    centerX, barY + BAR_HEIGHT + 3, 0xAAAAAA);
            return;
        }

        // Bearing relative to where the player is facing (MC yaw convention).
        double angleTo = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double rel = MathHelper.wrapDegrees(angleTo - player.getYaw());

        boolean healthLow = maxHealth > 0f && health < maxHealth / 3f;
        int markerColor = healthLow ? COLOR_MARKER_LOW : (dim ? COLOR_MARKER_DIM : COLOR_MARKER);

        if (rel < -90.0) {
            // Behind and to the left — chevron at the left edge meaning "turn left".
            ctx.drawShadowedText(tr, Text.literal("«"), barLeft - 7, barY - 2, markerColor);
        } else if (rel > 90.0) {
            ctx.drawShadowedText(tr, Text.literal("»"), barRight + 2, barY - 2, markerColor);
        } else {
            int markerX = barLeft + (int) Math.round((rel + 90.0) / 180.0 * BAR_WIDTH);
            ctx.fill(markerX - 1, barY - 2, markerX + 2, barY + BAR_HEIGHT + 2, markerColor);
        }

        // Distance + vertical hint + staleness note, centered under the bar.
        String label = String.format("%.0fm", dist);
        if (dy > 4) {
            label += " ▲"; // ▲ companion is above
        } else if (dy < -4) {
            label += " ▼"; // ▼ companion is below
        }
        if (stale) {
            label += " (last seen)";
        }
        ctx.drawCenteredShadowedText(tr, Text.literal(label), centerX, barY + BAR_HEIGHT + 3,
                dim ? 0xAAAAAA : 0xFFFFFF);
    }
}
