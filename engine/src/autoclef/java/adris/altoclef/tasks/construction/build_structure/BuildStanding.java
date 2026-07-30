package adris.altoclef.tasks.construction.build_structure;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.BuildOrder.Occupancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses where the companion should stand to place a given block, and guarantees it can get back out
 * again.
 *
 * <p>This is the part of paced building with real teeth. Instant placement had no notion of a body at
 * all; once there is one, two new ways to fail appear: standing in a cell the plan needs to fill, and
 * sealing yourself inside the structure you are building.
 */
public final class BuildStanding {
    private BuildStanding() {}

    /** Half-width of the candidate cube searched around a target, from reach minus the select margin. */
    private static final int SEARCH_RADIUS = 3;

    /** Cap on the escape search. Past this many reachable cells, "entombed" is not a credible verdict. */
    private static final int ESCAPE_VISIT_CAP = 8192;

    /** Blocks that make a standing position a bad idea regardless of geometry. */
    private static boolean dangerous(BlockState state) {
        return state.is(Blocks.LAVA)
            || state.is(Blocks.FIRE)
            || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE);
    }

    /**
     * Whether the companion could stand here.
     *
     * <p>Note the deliberate asymmetry: <em>occupancy</em> is checked against the plan's final state
     * (never stand where a block must go, never rely on a floor the plan is going to carve away), while
     * <em>footing</em> is checked against the world as it is right now (we have to fit here on this
     * tick). Bottom-up ordering is what reconciles the two — the floor course is placed early and is
     * what creates the standing positions for the courses above it, and stations are recomputed against
     * the live world every time one is needed.
     */
    public static boolean canStand(AltoClefController mod, Occupancy occ, BlockPos feet) {
        ServerLevel level = mod.getWorld();
        if (level == null) {
            return false;
        }
        // The plan must not need our body's cells, nor remove what we are standing on.
        if (occ.needsFilling(feet) || occ.needsFilling(feet.above())) {
            return false;
        }
        if (occ.willBeAir().contains(feet.below())) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState belowState = level.getBlockState(feet.below());
        if (feetState.blocksMotion() || headState.blocksMotion()) {
            return false;
        }
        FluidState fluid = feetState.getFluidState();
        if (!fluid.isEmpty()) {
            return false;
        }
        if (!belowState.blocksMotion()) {
            return false;
        }

        return !dangerous(belowState) && !dangerous(feetState) && !dangerous(headState);
    }

    /**
     * Whether a route out of the structure will still exist once the plan is finished.
     *
     * <p>A BFS over the <em>post-build</em> world: a cell counts as open if the plan will carve it, closed
     * if the plan will fill it, and otherwise whatever it is today. Escape means reaching any cell whose
     * horizontal position is outside the structure's footprint.
     *
     * <p>This is a guarantee rather than a heuristic, and the reason is worth stating: solid occupancy
     * only ever grows towards the final state, so connectivity only ever shrinks. A position that is
     * connected to the outside in the finished structure is therefore connected at every intermediate
     * step too. That holds on one condition — that any carve-outs the route depends on have already
     * happened — which is precisely why air cells are placed as a phase of their own, before anything
     * solid goes up.
     *
     * <p>Falling is a legal move. Without it a position on top of a finished roof looks entombed, when in
     * reality you walk to the edge and step off.
     */
    public static boolean hasEscape(AltoClefController mod, Occupancy occ, BlockPos start) {
        ServerLevel level = mod.getWorld();
        if (level == null) {
            return true;
        }

        int minY = occ.min().getY() - 1;
        int maxY = occ.max().getY() + 2;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty()) {
            if (seen.size() > ESCAPE_VISIT_CAP) {
                return true;
            }
            BlockPos at = queue.poll();
            if (!occ.insideFootprint(at, 1)) {
                return true;
            }

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos step = at.relative(dir).offset(0, dy, 0);
                    if (step.getY() < minY || step.getY() > maxY || seen.contains(step)) {
                        continue;
                    }
                    if (!standable(level, occ, step)) {
                        continue;
                    }
                    seen.add(step);
                    queue.add(step);
                }

                // Walking off an edge: drop through open air to the first thing we can stand on.
                BlockPos edge = at.relative(dir);
                if (open(level, occ, edge) && open(level, occ, edge.above())) {
                    BlockPos fall = edge.below();
                    while (fall.getY() >= minY && open(level, occ, fall)) {
                        fall = fall.below();
                    }
                    BlockPos landing = fall.above();
                    if (landing.getY() >= minY && !seen.contains(landing) && standable(level, occ, landing)) {
                        seen.add(landing);
                        queue.add(landing);
                    }
                }
            }
        }

        return false;
    }

    /** Post-build passability of a single cell. */
    private static boolean open(ServerLevel level, Occupancy occ, BlockPos pos) {
        if (occ.willBeAir().contains(pos)) {
            return true;
        }
        if (occ.needsFilling(pos)) {
            return false;
        }
        return !level.getBlockState(pos).blocksMotion();
    }

    /** A cell a body could occupy: open at foot and head height. */
    private static boolean standable(ServerLevel level, Occupancy occ, BlockPos feet) {
        return open(level, occ, feet) && open(level, occ, feet.above());
    }

    /**
     * Pick somewhere to stand from which {@code target} can be placed, or empty if there is nowhere.
     *
     * <p>Candidates are ordered cheapest-first: positions outside the structure's footprint before
     * positions inside it (perimeter work reads better and is less likely to trap anything), then nearest
     * to where the companion already is. The escape BFS is only run as candidates are consumed, so it
     * typically executes once or twice rather than once per candidate.
     *
     * <p>Emptiness here is a legitimate outcome, not a bug — the middle of a large flat roof genuinely
     * has nowhere to stand within reach. The caller falls back to placing those cells remotely.
     */
    public static Optional<BlockPos> chooseStation(AltoClefController mod, Occupancy occ, BlockPos target,
                                                   BlockPos from, Set<BlockPos> blacklist) {
        double reach = BuildReach.reach(mod) - BuildReach.SELECT_MARGIN;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos feet = target.offset(dx, dy, dz);
                    if (blacklist.contains(feet)) {
                        continue;
                    }
                    if (!BuildReach.withinReach(BuildReach.eyesAt(mod.getEntity(), feet), target, reach)) {
                        continue;
                    }
                    if (!canStand(mod, occ, feet)) {
                        continue;
                    }
                    if (!BuildReach.hasLineOfSight(mod.getWorld(), mod.getEntity(),
                            BuildReach.eyesAt(mod.getEntity(), feet), target)) {
                        continue;
                    }
                    candidates.add(feet);
                }
            }
        }

        candidates.sort(Comparator
                .comparingInt((BlockPos p) -> occ.insideFootprint(p, 0) ? 1 : 0)
                .thenComparingDouble(p -> p.distSqr(from)));

        for (BlockPos candidate : candidates) {
            if (hasEscape(mod, occ, candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    /**
     * Shortest route out of the structure that needs at most {@code maxDig} cells broken, for digging
     * free when the companion has managed to seal itself in despite the guarantee above (owner
     * interference, terrain the plan did not account for). Returns the cells to break, in order, or empty
     * if no such route exists within the budget.
     */
    public static List<BlockPos> escapeByDigging(AltoClefController mod, Occupancy occ, BlockPos start, int maxDig) {
        ServerLevel level = mod.getWorld();
        if (level == null) {
            return List.of();
        }

        int minY = occ.min().getY() - 1;
        int maxY = occ.max().getY() + 2;
        Set<BlockPos> seen = new HashSet<>();
        Deque<List<BlockPos>> queue = new ArrayDeque<>();
        Deque<BlockPos> positions = new ArrayDeque<>();
        queue.add(List.of());
        positions.add(start);
        seen.add(start);

        while (!positions.isEmpty()) {
            BlockPos at = positions.poll();
            List<BlockPos> dug = queue.poll();
            if (!occ.insideFootprint(at, 1)) {
                return dug;
            }
            if (dug.size() >= maxDig || seen.size() > ESCAPE_VISIT_CAP) {
                continue;
            }

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos step = at.relative(dir).offset(0, dy, 0);
                    if (step.getY() < minY || step.getY() > maxY || seen.contains(step)) {
                        continue;
                    }
                    seen.add(step);
                    if (standable(level, occ, step)) {
                        positions.add(step);
                        queue.add(dug);
                        continue;
                    }
                    // Blocked: allow breaking the obstruction, but never something the plan wants there.
                    if (occ.needsFilling(step) || occ.needsFilling(step.above())) {
                        continue;
                    }
                    List<BlockPos> next = new ArrayList<>(dug);
                    if (level.getBlockState(step).blocksMotion()) {
                        next.add(step);
                    }
                    if (level.getBlockState(step.above()).blocksMotion()) {
                        next.add(step.above());
                    }
                    if (next.size() > maxDig) {
                        continue;
                    }
                    positions.add(step);
                    queue.add(next);
                }
            }
        }

        return List.of();
    }
}
