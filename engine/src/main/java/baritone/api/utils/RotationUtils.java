package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RotationUtils {
   public static final double DEG_TO_RAD = Math.PI / 180.0;
   public static final double RAD_TO_DEG = 180.0 / Math.PI;
   private static final Vec3[] BLOCK_SIDE_MULTIPLIERS = new Vec3[]{
      new Vec3(0.5, 0.0, 0.5), new Vec3(0.5, 1.0, 0.5), new Vec3(0.5, 0.5, 0.0), new Vec3(0.5, 0.5, 1.0), new Vec3(0.0, 0.5, 0.5), new Vec3(1.0, 0.5, 0.5)
   };

   private RotationUtils() {
   }

   public static Rotation calcRotationFromCoords(BlockPos orig, BlockPos dest) {
      return calcRotationFromVec3d(new Vec3(orig.getX(), orig.getY(), orig.getZ()), new Vec3(dest.getX(), dest.getY(), dest.getZ()));
   }

   public static Rotation wrapAnglesToRelative(Rotation current, Rotation target) {
      return current.yawIsReallyClose(target) ? new Rotation(current.getYaw(), target.getPitch()) : target.subtract(current).normalize().add(current);
   }

   public static Rotation calcRotationFromVec3d(Vec3 orig, Vec3 dest, Rotation current) {
      return wrapAnglesToRelative(current, calcRotationFromVec3d(orig, dest));
   }

   public static Rotation calcRotationFromVec3d(Vec3 orig, Vec3 dest) {
      double[] delta = new double[]{orig.x - dest.x, orig.y - dest.y, orig.z - dest.z};
      double yaw = Mth.atan2(delta[0], -delta[2]);
      double dist = Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2]);
      double pitch = Mth.atan2(delta[1], dist);
      return new Rotation((float)(yaw * (180.0 / Math.PI)), (float)(pitch * (180.0 / Math.PI)));
   }

   public static Vec3 calcVector3dFromRotation(Rotation rotation) {
      float f = Mth.cos(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f1 = Mth.sin(-rotation.getYaw() * (float) (Math.PI / 180.0) - (float) Math.PI);
      float f2 = -Mth.cos(-rotation.getPitch() * (float) (Math.PI / 180.0));
      float f3 = Mth.sin(-rotation.getPitch() * (float) (Math.PI / 180.0));
      return new Vec3(f1 * f2, f3, f * f2);
   }

   public static Optional<Rotation> reachable(IEntityContext ctx, BlockPos pos) {
      return reachable(ctx.entity(), pos, ctx.playerController().getBlockReachDistance());
   }

   public static Optional<Rotation> reachable(IEntityContext ctx, BlockPos pos, boolean wouldSneak) {
      return reachable(ctx.entity(), pos, ctx.playerController().getBlockReachDistance(), wouldSneak);
   }

   public static Optional<Rotation> reachable(LivingEntity entity, BlockPos pos, double blockReachDistance) {
      return reachable(entity, pos, blockReachDistance, false);
   }

   public static Optional<Rotation> reachable(LivingEntity entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      IBaritone baritone = BaritoneAPI.getProvider().getBaritone(entity);
      if (baritone.getEntityContext().isLookingAt(pos)) {
         Rotation hypothetical = new Rotation(entity.getYRot(), entity.getXRot() + 1.0E-4F);
         if (!wouldSneak) {
            return Optional.of(hypothetical);
         }

         HitResult result = RayTraceUtils.rayTraceTowards(entity, hypothetical, blockReachDistance, true);
         if (result != null && result.getType() == Type.BLOCK && ((BlockHitResult)result).getBlockPos().equals(pos)) {
            return Optional.of(hypothetical);
         }
      }

      Optional<Rotation> possibleRotation = reachableCenter(entity, pos, blockReachDistance, wouldSneak);
      if (possibleRotation.isPresent()) {
         return possibleRotation;
      } else {
         BlockState state = entity.level().getBlockState(pos);
         VoxelShape shape = state.getShape(entity.level(), pos);
         if (shape.isEmpty()) {
            shape = Shapes.block();
         }

         for (Vec3 sideOffset : BLOCK_SIDE_MULTIPLIERS) {
            double xDiff = shape.min(Axis.X) * sideOffset.x + shape.max(Axis.X) * (1.0 - sideOffset.x);
            double yDiff = shape.min(Axis.Y) * sideOffset.y + shape.max(Axis.Y) * (1.0 - sideOffset.y);
            double zDiff = shape.min(Axis.Z) * sideOffset.z + shape.max(Axis.Z) * (1.0 - sideOffset.z);
            possibleRotation = reachableOffset(
               entity, pos, new Vec3(pos.getX(), pos.getY(), pos.getZ()).add(xDiff, yDiff, zDiff), blockReachDistance, wouldSneak
            );
            if (possibleRotation.isPresent()) {
               return possibleRotation;
            }
         }

         return Optional.empty();
      }
   }

   public static Optional<Rotation> reachableOffset(Entity entity, BlockPos pos, Vec3 offsetPos, double blockReachDistance, boolean wouldSneak) {
      Vec3 eyes = wouldSneak ? RayTraceUtils.inferSneakingEyePosition(entity) : entity.getEyePosition(1.0F);
      Rotation rotation = calcRotationFromVec3d(eyes, offsetPos, new Rotation(entity.getYRot(), entity.getXRot()));
      HitResult result = RayTraceUtils.rayTraceTowards(entity, rotation, blockReachDistance, wouldSneak);
      if (result != null && result.getType() == Type.BLOCK) {
         if (((BlockHitResult)result).getBlockPos().equals(pos)) {
            return Optional.of(rotation);
         }

         if (entity.level().getBlockState(pos).getBlock() instanceof BaseFireBlock && ((BlockHitResult)result).getBlockPos().equals(pos.below())) {
            return Optional.of(rotation);
         }
      }

      return Optional.empty();
   }

   public static Optional<Rotation> reachableCenter(Entity entity, BlockPos pos, double blockReachDistance, boolean wouldSneak) {
      return reachableOffset(entity, pos, VecUtils.calculateBlockCenter(entity.level(), pos), blockReachDistance, wouldSneak);
   }
}
