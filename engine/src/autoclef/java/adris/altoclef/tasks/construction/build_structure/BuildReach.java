package adris.altoclef.tasks.construction.build_structure;

import adris.altoclef.AltoClefController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reach and line-of-sight tests for placing a block.
 *
 * <p><b>Why this exists instead of {@code RotationUtils.reachable}.</b> That helper raytraces towards a
 * position and only succeeds if the resulting {@code BlockHitResult} lands <em>on</em> that position. A
 * block we are about to place is air, so the ray passes straight through it and reports whatever is
 * behind — it returns "not reachable" for every legal placement, and a build gated on it would never
 * place anything. {@code FarmProcess} can use it because crops and farmland are already solid.
 *
 * <p>So reach is measured directly (eye to the nearest point of the target's box) and occlusion is a
 * separate raytrace that is allowed to stop short. A pleasant side effect of the occlusion test is that
 * "don't build through a wall" comes out for free: a ray aimed at a cell behind something already solid
 * hits the solid thing first.
 */
public final class BuildReach {
    private BuildReach() {}

    /**
     * Safety margin taken off reach when choosing somewhere to stand, in blocks.
     *
     * <p>Standing positions are predicted from a block centre, but {@code GoalBlock} arrival only
     * guarantees the companion's {@code blockPosition()} matches — its actual x/z can sit up to ~0.4
     * off centre, and its eye height moves with pose. Selecting at full reach therefore picks stations
     * from which a cell turns out to be a few hundredths too far, which reads as the companion walking
     * somewhere and then doing nothing. Selection is conservative; execution re-tests exactly.
     */
    public static final double SELECT_MARGIN = 0.75;

    /** Vanilla survival block reach, read from the interaction controller so there is one source. */
    public static double reach(AltoClefController mod) {
        return mod.getBaritone().getEntityContext().playerController().getBlockReachDistance();
    }

    /** Where the companion's eyes would be if it stood in the middle of {@code feet}. */
    public static Vec3 eyesAt(LivingEntity entity, BlockPos feet) {
        return new Vec3(feet.getX() + 0.5, feet.getY() + entity.getEyeHeight(), feet.getZ() + 0.5);
    }

    /** Eye-to-nearest-corner distance, matching how vanilla measures interaction range. */
    public static boolean withinReach(Vec3 eyes, BlockPos pos, double reach) {
        if (eyes == null || pos == null) {
            return false;
        }

        AABB box = new AABB(pos);
        double dx = Math.max(0.0, Math.max(box.minX - eyes.x, eyes.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - eyes.y, eyes.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - eyes.z, eyes.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /**
     * Whether anything solid stands between those eyes and that cell.
     *
     * <p>Tries the cell centre first and then each face centre, the same widening
     * {@code RotationUtils.reachable} does, because a cell tucked under an eave is often only visible
     * from one side. Uses {@code COLLIDER} rather than {@code OUTLINE} so grass, torches and other
     * non-colliding scenery do not count as walls.
     */
    public static boolean hasLineOfSight(Level level, Entity entity, Vec3 eyes, BlockPos pos) {
        if (level == null || eyes == null || pos == null) {
            return false;
        }
        if (visible(level, entity, eyes, pos, Vec3.atCenterOf(pos))) {
            return true;
        }

        for (Direction face : Direction.values()) {
            Vec3 facePoint = Vec3.atCenterOf(pos).add(
                    face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (visible(level, entity, eyes, pos, facePoint)) {
                return true;
            }
        }

        return false;
    }

    private static boolean visible(Level level, Entity entity, Vec3 eyes, BlockPos pos, Vec3 aim) {
        HitResult hit = level.clip(new ClipContext(eyes, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos)) {
            return true;
        }

        // Stopped at or beyond the point we were aiming at — nothing is actually in the way.
        return hit.getLocation().distanceToSqr(eyes) >= aim.distanceToSqr(eyes) - 1.0E-4;
    }
}
