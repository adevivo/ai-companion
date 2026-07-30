package adris.altoclef.tasks.construction.build_structure;

import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Turns a raw DSL plan into an order a body can actually build in.
 *
 * <p>The old placement path wrote blocks in whatever order the plan listed them, which was fine when
 * every write was instantaneous and position-independent. Once the companion has to stand somewhere and
 * reach each block, order is what decides whether the build is possible at all.
 */
public final class BuildOrder {
    private BuildOrder() {}

    /** One resolved placement: where, and what block goes there. */
    public record Cell(BlockPos pos, Block block, boolean isAir) {}

    /**
     * What the plan will have done to the world once it finishes.
     *
     * <p>Used for two different questions, and the distinction matters: {@code willBeSolid} says "do not
     * stand here, something has to go here", while {@code willBeAir} says "this will be carved out, so a
     * route through it is legitimate even though it is stone right now".
     */
    public record Occupancy(Set<BlockPos> willBeSolid, Set<BlockPos> willBeAir,
                            BlockPos min, BlockPos max) {
        public boolean touches(BlockPos pos) {
            return willBeSolid.contains(pos) || willBeAir.contains(pos);
        }

        /** True when the plan needs this cell filled, so the companion must not be standing in it. */
        public boolean needsFilling(BlockPos pos) {
            return willBeSolid.contains(pos);
        }

        /** True when this cell is inside the structure's footprint, inflated horizontally by {@code pad}. */
        public boolean insideFootprint(BlockPos pos, int pad) {
            return pos.getX() >= min.getX() - pad && pos.getX() <= max.getX() + pad
                && pos.getZ() >= min.getZ() - pad && pos.getZ() <= max.getZ() + pad;
        }
    }

    /**
     * Deduplicate and reorder a plan.
     *
     * <p><b>Dedupe has to happen before any reordering</b>, and last write wins. {@code setBlock}
     * semantics mean a position written twice ends as whatever was written last, so a DSL program that
     * places a wall and then carves a window out of it relies on order. Reordering such a plan without
     * collapsing duplicates first would silently fill the window back in. Collapsing them makes the
     * reorder safe and, as a bonus, stops the material bill charging twice for one cell.
     *
     * <p>Ordering is three keys deep:
     * <ol>
     * <li><b>Y ascending.</b> Bottom-up, so the companion is never standing in a cell that still needs
     * filling, and so each finished course becomes standable ground for the one above it.
     * <li><b>Distance from the layer's centre, descending</b> (bucketed to whole blocks). Outside-in,
     * which is what makes a flat roof partly self-scaffolding: the outer ring is reachable from the
     * ground outside, and once it exists the companion can stand on it and reach further in.
     * <li><b>Angle around that centre.</b> Turns a ring into a walk round the outside rather than a
     * zig-zag across it.
     * </ol>
     */
    public static List<SetBlockCommand> normalise(List<SetBlockCommand> plan) {
        LinkedHashMap<BlockPos, SetBlockCommand> collapsed = new LinkedHashMap<>();
        for (SetBlockCommand command : plan) {
            // put() on an existing key replaces the value and keeps the original insertion order, which
            // is exactly last-write-wins.
            collapsed.put(new BlockPos(command.x, command.y, command.z), command);
        }

        List<SetBlockCommand> commands = new ArrayList<>(collapsed.values());
        if (commands.isEmpty()) {
            return commands;
        }

        double centreX = commands.stream().mapToInt(c -> c.x).average().orElse(0.0);
        double centreZ = commands.stream().mapToInt(c -> c.z).average().orElse(0.0);
        commands.sort(Comparator
                .comparingInt((SetBlockCommand c) -> c.y)
                .thenComparingInt(c -> -radiusBucket(c, centreX, centreZ))
                .thenComparingDouble(c -> Math.atan2(c.z - centreZ, c.x - centreX)));
        return commands;
    }

    /**
     * Resolve a normalised plan into cells, index-for-index, so callers can keep using the command list
     * as the canonical plan (it still feeds the material bill and the plan cache) while working from
     * resolved blocks. Entries whose block name does not resolve come back null and should be skipped.
     */
    public static List<Cell> cellsOf(List<SetBlockCommand> plan) {
        List<Cell> cells = new ArrayList<>(plan.size());
        for (SetBlockCommand command : plan) {
            Block block = BuildMaterials.resolveBlock(command.blockName);
            cells.add(block == null
                    ? null
                    : new Cell(new BlockPos(command.x, command.y, command.z), block, WorldHelper.isAir(block)));
        }
        return cells;
    }

    private static int radiusBucket(SetBlockCommand command, double centreX, double centreZ) {
        double dx = command.x - centreX;
        double dz = command.z - centreZ;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    /**
     * Build the occupancy model.
     *
     * <p>Must be called <em>after</em> the ground check, because that check may shift the whole plan
     * vertically — an occupancy built from the unshifted plan is wrong by up to {@code MAX_LIFT} blocks
     * and would reject perfectly good standing positions.
     */
    public static Occupancy occupancyOf(List<Cell> cells) {
        Set<BlockPos> solid = new HashSet<>();
        Set<BlockPos> air = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;
        for (Cell cell : cells) {
            if (cell == null) {
                continue;
            }
            any = true;
            BlockPos pos = cell.pos();
            if (cell.isAir()) {
                air.add(pos);
            } else {
                solid.add(pos);
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (!any) {
            BlockPos origin = BlockPos.ZERO;
            return new Occupancy(solid, air, origin, origin);
        }

        return new Occupancy(solid, air, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }
}
