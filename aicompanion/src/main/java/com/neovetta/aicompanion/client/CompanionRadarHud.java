package com.neovetta.aicompanion.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side locator bar that points toward the companion so the owner can walk to it without
 * recalling. Works past entity-tracking range because the server pushes coordinates
 * ({@link com.neovetta.aicompanion.AiCompanion#RADAR_UPDATE}); this class just holds the last snapshot
 * and renders it.
 *
 * <p>State is static and session-scoped (mode is not persisted — not worth a client config file for
 * v1). Snapshots are keyed by entity id and written from a netty thread while the HUD callback reads
 * them on the render thread; each snapshot is immutable and swapped in whole, so a reader sees either
 * the old one or the new one and never a mix.
 *
 * <p>One entry per companion. It used to be a single set of fields, which was fine with one companion
 * and actively wrong with two: both pushed into the same slot ten times a second, so the bar showed
 * whichever packet landed last and the marker jumped between two bodies.
 */
public final class CompanionRadarHud {

    private CompanionRadarHud() {}

    /** Radar visibility. AUTO hides the bar when the companion is close and not stale/cross-dimension. */
    public enum Mode { AUTO, ON, OFF }

    // Default ON: the bar is always visible whenever a companion is reporting in. AUTO (auto-hide when
    // close) and OFF remain reachable via /companion radar or the keybind.
    private static volatile Mode mode = Mode.ON;

    /** One companion's last reported position, health and name. */
    private record Snapshot(String name, double x, double y, double z, Identifier worldId,
                            float health, float maxHealth, long receivedAtMs) {}

    /** entityId → last snapshot. Written from netty, read from the render thread. */
    private static final Map<Integer, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    /** Store a fresh snapshot (called from the packet receiver). */
    public static void update(int entityId, String name, double x, double y, double z,
                              Identifier worldId, float health, float maxHealth) {
        SNAPSHOTS.put(entityId, new Snapshot(name, x, y, z, worldId, health, maxHealth,
                System.currentTimeMillis()));
    }

    /**
     * Forget every companion. Called on disconnect: the snapshots are static and would otherwise
     * outlive the world, so joining a second world within the give-up window would draw markers for
     * companions belonging to the first one.
     */
    public static void clear() {
        SNAPSHOTS.clear();
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

        // Decide what to draw before drawing any of it: the bar itself is shared, so it must not be
        // painted at all if every companion turns out to be hidden by AUTO or aged out.
        List<Reading> readings = new ArrayList<>();
        for (Map.Entry<Integer, Snapshot> entry : SNAPSHOTS.entrySet()) {
            Snapshot snap = entry.getValue();
            long age = now - snap.receivedAtMs();
            if (age > GIVE_UP_MS) {
                SNAPSHOTS.remove(entry.getKey()); // despawned, or long gone — stop tracking it
                continue;
            }
            boolean crossDim = snap.worldId() == null || !here.equals(snap.worldId());
            boolean stale = age > STALE_MS;
            double dx = snap.x() - player.getX();
            double dy = snap.y() - player.getY();
            double dz = snap.z() - player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // AUTO shows only when it's actually useful: far away, stale, or another dimension.
            if (mode == Mode.AUTO && !crossDim && !stale && dist <= 16.0) {
                continue;
            }
            readings.add(new Reading(snap, dist, dy, crossDim, stale));
        }
        if (readings.isEmpty()) {
            return;
        }
        // Nearest last, so the one you are most likely walking to is drawn on top of the others.
        readings.sort(Comparator.comparingDouble(Reading::dist).reversed());

        boolean allDim = readings.stream().allMatch(r -> r.stale() || r.crossDim());
        int screenW = ctx.getScaledWindowWidth();
        int screenH = ctx.getScaledWindowHeight();
        int centerX = screenW / 2;
        int barLeft = centerX - BAR_WIDTH / 2;
        int barRight = barLeft + BAR_WIDTH;
        int barY = screenH - BAR_BOTTOM_OFFSET;
        TextRenderer tr = client.textRenderer;

        // Bar background + border + center tick.
        ctx.fill(barLeft - 1, barY - 1, barRight + 1, barY + BAR_HEIGHT + 1, (allDim ? 0x40 : 0x80) << 24);
        ctx.fill(barLeft, barY, barRight, barY + BAR_HEIGHT, allDim ? 0x20FFFFFF : 0x40FFFFFF);
        ctx.fill(centerX, barY - 2, centerX + 1, barY + BAR_HEIGHT + 2, allDim ? 0x60FFFFFF : 0xA0FFFFFF);

        // One marker per companion on the shared bar; one label line each beneath it. Names are only
        // worth the space once there is more than one to tell apart.
        boolean showNames = readings.size() > 1;
        int labelY = barY + BAR_HEIGHT + 3;
        for (Reading r : readings) {
            Snapshot snap = r.snapshot();
            boolean dim = r.stale() || r.crossDim();
            boolean healthLow = snap.maxHealth() > 0f && snap.health() < snap.maxHealth() / 3f;
            int markerColor = healthLow ? COLOR_MARKER_LOW : (dim ? COLOR_MARKER_DIM : COLOR_MARKER);
            String prefix = showNames ? snap.name() + " " : "";

            if (r.crossDim()) {
                ctx.drawCenteredShadowedText(tr, Text.literal(prefix + "other dimension"),
                        centerX, labelY, 0xAAAAAA);
                labelY += 10;
                continue;
            }

            // Bearing relative to where the player is facing (MC yaw convention).
            double angleTo = Math.toDegrees(Math.atan2(snap.z() - player.getZ(),
                    snap.x() - player.getX())) - 90.0;
            double rel = MathHelper.wrapDegrees(angleTo - player.getYaw());
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
            String label = prefix + String.format("%.0fm", r.dist());
            if (r.dy() > 4) {
                label += " ▲"; // ▲ companion is above
            } else if (r.dy() < -4) {
                label += " ▼"; // ▼ companion is below
            }
            if (r.stale()) {
                label += " (last seen)";
            }
            ctx.drawCenteredShadowedText(tr, Text.literal(label), centerX, labelY,
                    dim ? 0xAAAAAA : 0xFFFFFF);
            labelY += 10;
        }
    }

    /** A snapshot worked out relative to the player, ready to draw. */
    private record Reading(Snapshot snapshot, double dist, double dy, boolean crossDim, boolean stale) {}
}
