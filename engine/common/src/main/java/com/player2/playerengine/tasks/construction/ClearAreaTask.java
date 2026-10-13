package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.tasks.base.Task;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Remove every breakable block in an inclusive box, nearest first.
 *
 * <p>Deliberately a thin loop over {@link DestroyBlockTask} rather than a new digger: that task
 * already hands the block to the builder process, which owns reach, standing position and tool
 * choice. BuildStructureTask's CARVE stage is the same machinery wrapped in a plan-wide state machine
 * (approach, fence-aware standing cells), which a "clear this pen" request does not need.
 *
 * <p>⚠️ Blocks are filtered up front, and again when reached, because the model aims this at a
 * region it only half knows: a chest in the box must survive, bedrock must not stall the run, and
 * lava or water cannot be "broken" at all.
 */
public class ClearAreaTask extends Task {

    /** Tries per block before giving up on it; a block the builder cannot reach would loop forever. */
    private static final int MAX_ATTEMPTS = 2;

    private final BlockPos min;
    private final BlockPos max;

    private final List<BlockPos> remaining = new ArrayList<>();
    private final Map<String, Integer> skipped = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private int removed;
    private BlockPos current;
    private DestroyBlockTask currentTask;
    private boolean finished;

    public ClearAreaTask(BlockPos a, BlockPos b) {
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** Why this block must be left alone, or null if it may be broken. */
    static String skipReason(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof LiquidBlock) {
            return "fluid";
        }
        if (state.hasBlockEntity()) {
            return "container or other block entity (chest, furnace, bed, sign...)";
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return "unbreakable";
        }
        return null;
    }

    private void skip(String why) {
        skipped.merge(why, 1, Integer::sum);
    }

    @Override
    protected void onStart() {
        remaining.clear();
        skipped.clear();
        attempts.clear();
        removed = 0;
        current = null;
        currentTask = null;
        finished = false;
        Level level = controller.getWorld();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            String why = skipReason(state, level, pos);
            if (why != null) {
                skip(why);
            } else {
                remaining.add(pos.immutable());
            }
        }
    }

    @Override
    protected Task onTick() {
        Level level = controller.getWorld();
        if (current != null && currentTask.isFinished()) {
            if (level.getBlockState(current).isAir()) {
                removed++;
                remaining.remove(current);
            } else if (attempts.merge(current, 1, Integer::sum) >= MAX_ATTEMPTS) {
                skip("could not be reached or broken");
                remaining.remove(current);
            }
            current = null;
            currentTask = null;
        }
        if (current == null) {
            // Something else (a creeper, the owner) may have cleared blocks meanwhile; do not count
            // those as ours.
            remaining.removeIf(p -> level.getBlockState(p).isAir());
            // Re-filter: a cell someone else broke can fill with water, which the builder cannot "break".
            remaining.removeIf(p -> {
               String why = skipReason(level.getBlockState(p), level, p);
               if (why != null) {
                  skip(why);
               }
               return why != null;
            });
            if (remaining.isEmpty()) {
                finished = true;
                return null;
            }
            BlockPos here = controller.getPlayer().blockPosition();
            current = remaining.stream()
                    .min(Comparator.comparingDouble(p -> p.distSqr(here)))
                    .get();
            currentTask = new DestroyBlockTask(current);
        }
        setDebugState("Clearing " + current.toShortString() + ", " + remaining.size() + " left.");
        return currentTask;
    }

    private String summary(String lead) {
        StringBuilder sb = new StringBuilder(lead).append(": removed ").append(removed).append(" block(s)");
        if (!skipped.isEmpty()) {
            int total = skipped.values().stream().mapToInt(Integer::intValue).sum();
            sb.append(", skipped ").append(total).append(" (");
            boolean first = true;
            for (Map.Entry<String, Integer> e : skipped.entrySet()) {
                sb.append(first ? "" : "; ").append(e.getValue()).append(" ").append(e.getKey());
                first = false;
            }
            sb.append(")");
        }
        return sb.append(".").toString();
    }

    @Override
    protected void onStop(Task interruptTask) {
        // Report on completion and on interruption alike, so the model always learns the tally.
        controller.logAgentInfo(summary(finished ? "Clear finished" : "Clear stopped early"));
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof ClearAreaTask t && t.min.equals(min) && t.max.equals(max);
    }

    @Override
    protected String toDebugString() {
        return "Clearing " + min.toShortString() + " to " + max.toShortString();
    }
}
