package baritone.api.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RayTraceUtils {
   public static Fluid fluidHandling = Fluid.NONE;

   private RayTraceUtils() {
   }

   public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance) {
      return rayTraceTowards(entity, rotation, blockReachDistance, false);
   }

   public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance, boolean wouldSneak) {
      Vec3 start;
      if (wouldSneak) {
         start = inferSneakingEyePosition(entity);
      } else {
         start = entity.getEyePosition(1.0F);
      }

      Vec3 direction = RotationUtils.calcVector3dFromRotation(rotation);
      Vec3 end = start.add(direction.x * blockReachDistance, direction.y * blockReachDistance, direction.z * blockReachDistance);
      return entity.level().clip(new ClipContext(start, end, Block.OUTLINE, fluidHandling, entity));
   }

   public static Vec3 inferSneakingEyePosition(Entity entity) {
      return new Vec3(
         entity.getX(),
         entity.getY() + ((IEntityAccessor)entity).automatone$invokeGetEyeHeight(Pose.CROUCHING, entity.getDimensions(Pose.CROUCHING)),
         entity.getZ()
      );
   }
}
