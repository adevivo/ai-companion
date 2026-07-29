package adris.altoclef.tasks.construction.build_structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.construction.build_structure.StructureFromCode.SetBlockCommand;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Checks a generated build plan against the terrain it is about to be written into.
 *
 * <p>The DSL writes blocks by absolute coordinate, and the Y in those coordinates comes from a model
 * that has no reliable sense of where the ground is. A plan that misses by two blocks is either
 * buried out of sight or standing in the air, and either way the materials are spent. This works out
 * how far off the plan is so the caller can nudge a near miss onto the surface and reject a wild one
 * before charging for it.
 */
public final class BuildPlacement {

    private BuildPlacement() {
    }

    /**
     * How far a plan may be buried and still be lifted onto the surface rather than rejected.
     *
     * <p>Only ever applied upward. Underground is the one direction that is never intentional for a
     * requested build and, worse, is invisible — the materials are spent and the player sees nothing.
     *
     * <p>{@code WorldHelper.UNDERGROUND_THRESHOLD} is kept equal to this. Everything within this
     * distance of the sky surface is a case handled correctly here, so the terrain read stays the plain
     * heightmap for those; only offsets larger than this — which land in the refusal branch below — go
     * looking for a local floor. Change one and change the other.
     */
    public static final int MAX_LIFT = 3;

    /**
     * How high above the terrain a plan may start before it is treated as a hallucinated coordinate.
     *
     * <p>Deliberately generous, and deliberately not symmetric with {@link #MAX_LIFT}. A plan sitting
     * above the ground is usually exactly what was asked for — "put it on top of the ground" is a
     * one-block gap, and towers, platforms and bridges are legitimately higher. An earlier symmetric
     * rule quietly dragged a "build it one block higher" request back down flush with the terrain,
     * because a one-block gap is indistinguishable from a one-block mistake by size alone. Only a
     * plan far enough up that nothing plausible was asked for gets rejected.
     */
    public static final int MAX_AIR_GAP = 16;

    /**
     * Signed distance from the plan's base layer to the ground, in blocks: positive means the plan
     * sits below the surface and must move up.
     *
     * <p>Computed per column as {@code terrainY - planBaseY} and reduced with a median rather than a
     * mean. A single uniform shift cannot fit a sloped site, so the goal is the offset that suits the
     * bulk of the footprint; a mean would let a few columns over a cliff edge or a pond drag the
     * whole structure.
     *
     * <p>{@code terrainY} comes from {@link WorldHelper#groundYNear} rather than the sky heightmap, so
     * a build inside a cave measures against the cave floor. Reading the heightmap made every
     * underground build measure as tens of blocks buried and be refused, whatever Y was asked for.
     *
     * @return empty when there is nothing to compare — a plan of nothing but air (a pure carve-out),
     *         or a footprint whose chunks are not loaded
     */
    public static OptionalInt groundOffset(AltoClefController mod, List<SetBlockCommand> plan) {
        Map<Long, Integer> planBase = columnBases(plan);
        if (planBase.isEmpty()) {
            return OptionalInt.empty();
        }

        int minBuildHeight = mod.getWorld().getMinBuildHeight();
        List<Integer> offsets = new ArrayList<>(planBase.size());
        for (Map.Entry<Long, Integer> entry : planBase.entrySet()) {
            int x = unpackX(entry.getKey());
            int z = unpackZ(entry.getKey());
            // Measured from the plan's own base layer, not from the sky: inside a cave the heightmap
            // reports the terrain overhead, which made every underground build measure as tens of
            // blocks buried and be refused. Above ground this is the same heightmap value as before.
            int terrain = WorldHelper.groundYNear(mod, x, z, entry.getValue());
            if (terrain < minBuildHeight) {
                // Unloaded chunk — Level#getHeight answers with the floor of the world for those.
                // Treating that as terrain would make every offset enormous and reject the build.
                continue;
            }
            offsets.add(terrain - entry.getValue());
        }
        if (offsets.isEmpty()) {
            return OptionalInt.empty();
        }
        Collections.sort(offsets);
        return OptionalInt.of(offsets.get(offsets.size() / 2));
    }

    /**
     * The lowest layer the plan places a solid block on, across the whole footprint.
     *
     * <p>Paired with the offset from {@link #groundOffset}, this is what turns that offset back into
     * an absolute Y — so a refusal can quote the ground it actually measured instead of the
     * companion's feet, which are somewhere else entirely whenever the plan is.
     */
    public static OptionalInt planBaseY(List<SetBlockCommand> plan) {
        return columnBases(plan).values().stream().mapToInt(Integer::intValue).min();
    }

    /**
     * Lowest solid block the plan places in each column, keyed by {@link #columnKey}.
     *
     * <p>Air placements are skipped: the DSL emits them to hollow out interiors and carve doorways,
     * so counting one as the base would measure the bottom of a hole instead of the bottom of the
     * structure.
     */
    private static Map<Long, Integer> columnBases(List<SetBlockCommand> plan) {
        Map<Long, Integer> bases = new HashMap<>();
        for (SetBlockCommand command : plan) {
            Block block = BuildMaterials.resolveBlock(command.blockName);
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                continue;
            }
            bases.merge(columnKey(command.x, command.z), command.y, Math::min);
        }
        return bases;
    }

    /** The same plan moved vertically by {@code dy}, preserving all relative geometry. */
    public static List<SetBlockCommand> shifted(List<SetBlockCommand> plan, int dy) {
        List<SetBlockCommand> moved = new ArrayList<>(plan.size());
        for (SetBlockCommand command : plan) {
            moved.add(new SetBlockCommand(command.x, command.y + dy, command.z, command.blockName));
        }
        return moved;
    }

    // Column identity as a single long so the map needs no allocation per block. x and z are
    // absolute block coordinates and can be negative, hence the mask-and-shift rather than a hash.
    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
