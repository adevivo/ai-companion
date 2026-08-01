package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Cut a staircase down at 45 degrees — one block down for every block along — that can be walked
 * back up.
 *
 * <p>This existed as a skill first: prose telling the model to work out
 * {@code goto (Xs + 4n·dx) (Ys − 4n) (Zs + 4n·dz)} for n = 1, 2, 3… and to watch {@code taskStatus}
 * between turns to know when to issue the next leg. That asks a language model for multi-step
 * arithmetic and cross-turn state, which is the pair of things it is worst at, and across several
 * play sessions it never once produced a staircase — it invented commands, mis-added coordinates, or
 * reached for {@code build_structure}, which places blocks rather than removing them.
 *
 * <p>The geometry is fixed and knowable, so nothing about it needed a model. The agent now issues one
 * command and the legs, the arithmetic and the safety checks all happen here.
 *
 * <p>Straight down is deliberately not offered. A vertical shaft is a hole you cannot climb out of
 * and the classic way to fall into lava or a cavern; the diagonal is what makes the result usable.
 */
public class DigStaircaseTask extends Task {

    /** Blocks per leg. Short legs keep the slope even — one long goto takes the cheapest route. */
    private static final int LEG_LENGTH = 4;

    /** Bedrock starts around -60; stop clear of it rather than grinding against it. */
    private static final int MIN_SAFE_Y = -55;

    /** Close enough to call a leg done. Baritone lands next to a target as often as on it. */
    private static final double ARRIVAL_TOLERANCE_SQ = 6.0;

    private final int dx;
    private final int dz;
    private final int blocksDown;
    private final String directionName;

    private BlockPos start;
    private BlockPos legTarget;
    private int legsDone;
    private boolean finished;
    private String abortReason;

    public DigStaircaseTask(int dx, int dz, int blocksDown, String directionName) {
        this.dx = dx;
        this.dz = dz;
        this.blocksDown = blocksDown;
        this.directionName = directionName;
    }

    @Override
    protected void onStart() {
        this.start = this.controller.getPlayer().blockPosition();
        this.legsDone = 0;
        this.finished = false;
        this.abortReason = null;
        this.legTarget = null;
        this.controller.logAgentInfo(String.format(
                "Cutting a staircase %s from %d %d %d, down %d blocks to y=%d.",
                directionName, start.getX(), start.getY(), start.getZ(),
                blocksDown, start.getY() - blocksDown));
    }

    @Override
    protected Task onTick() {
        if (finished) {
            return null;
        }
        AltoClefController mod = this.controller;
        BlockPos here = mod.getPlayer().blockPosition();

        if (legTarget == null || here.distSqr(legTarget) <= ARRIVAL_TOLERANCE_SQ) {
            if (legTarget != null) {
                legsDone++;
            }
            if (!advance(here)) {
                return null;
            }
        }

        setDebugState(String.format("leg %d, heading %s to %d %d %d",
                legsDone + 1, directionName, legTarget.getX(), legTarget.getY(), legTarget.getZ()));
        // preferStairs: ask the pathfinder for a walkable descent rather than a drop, which is the
        // whole point of digging diagonally.
        return new GetToBlockTask(legTarget, true);
    }

    /** Pick the next leg, or stop. Returns false when the staircase is over. */
    private boolean advance(BlockPos here) {
        AltoClefController mod = this.controller;
        int cutSoFar = start.getY() - here.getY();
        if (cutSoFar >= blocksDown) {
            stopWith(String.format(
                    "Staircase finished. The bottom step is at %d %d %d, %d blocks down, and it walks "
                            + "straight back up to %d %d %d.",
                    here.getX(), here.getY(), here.getZ(), cutSoFar,
                    start.getX(), start.getY(), start.getZ()));
            return false;
        }

        int remaining = blocksDown - cutSoFar;
        int step = Math.min(LEG_LENGTH, remaining);
        BlockPos next = new BlockPos(
                here.getX() + step * dx,
                here.getY() - step,
                here.getZ() + step * dz);

        if (next.getY() < MIN_SAFE_Y) {
            stopWith(String.format(
                    "Stopping at %d %d %d — the next leg would reach y=%d and bedrock starts just "
                            + "below. Cut %d blocks of the %d asked for.",
                    here.getX(), here.getY(), here.getZ(), next.getY(), cutSoFar, blocksDown));
            return false;
        }

        String hazard = hazardNear(mod, next);
        if (hazard != null) {
            stopWith(String.format(
                    "Stopping at %d %d %d — %s ahead at %d %d %d. Cut %d blocks of the %d asked for.",
                    here.getX(), here.getY(), here.getZ(), hazard,
                    next.getX(), next.getY(), next.getZ(), cutSoFar, blocksDown));
            return false;
        }

        this.legTarget = next;
        return true;
    }

    /**
     * Name the fluid in or around {@code target}, or null when it is safe to dig into.
     *
     * <p>Checked before the leg rather than after, because the point is to not open the wall in the
     * first place — path into lava and the report that it happened is no use to anybody.
     */
    private static String hazardNear(AltoClefController mod, BlockPos target) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos probe = target.offset(x, y, z);
                    BlockState state = mod.getWorld().getBlockState(probe);
                    FluidState fluid = state.getFluidState();
                    if (state.is(Blocks.LAVA) || (!fluid.isEmpty() && fluid.is(net.minecraft.tags.FluidTags.LAVA))) {
                        return "lava";
                    }
                    if (state.is(Blocks.WATER) || (!fluid.isEmpty() && fluid.is(net.minecraft.tags.FluidTags.WATER))) {
                        return "water";
                    }
                }
            }
        }
        return null;
    }

    private void stopWith(String reason) {
        this.abortReason = reason;
        this.finished = true;
        this.legTarget = null;
        this.controller.logAgentInfo(reason);
    }

    @Override
    protected void onStop(Task interruptTask) {
        if (!finished && abortReason == null) {
            BlockPos here = this.controller.getPlayer().blockPosition();
            this.controller.logAgentInfo(String.format(
                    "Staircase stopped early at %d %d %d, %d blocks down of the %d asked for.",
                    here.getX(), here.getY(), here.getZ(), start.getY() - here.getY(), blocksDown));
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof DigStaircaseTask task
                && task.dx == this.dx && task.dz == this.dz && task.blocksDown == this.blocksDown;
    }

    @Override
    protected String toDebugString() {
        return String.format("Digging a staircase %s, %d blocks down", directionName, blocksDown);
    }
}
