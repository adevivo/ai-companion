package adris.altoclef.util.helpers;

import adris.altoclef.Debug;
import adris.altoclef.util.baritone.CachedProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public class ProjectileHelper {
   public static final double ARROW_GRAVITY_ACCEL = 0.05F;
   public static final double THROWN_ENTITY_GRAVITY_ACCEL = 0.03;

   public static boolean hasGravity(Projectile entity) {
      return entity instanceof AbstractHurtingProjectile ? false : !entity.isNoGravity();
   }

   private static Vec3 getClosestPointOnFlatLine(double shootX, double shootZ, double velX, double velZ, double playerX, double playerZ) {
      double deltaX = playerX - shootX;
      double deltaZ = playerZ - shootZ;
      double t = (velX * deltaX + velZ * deltaZ) / (velX * velX + velZ * velZ);
      double hitX = shootX + velX * t;
      double hitZ = shootZ + velZ * t;
      return new Vec3(hitX, 0.0, hitZ);
   }

   public static double getFlatDistanceSqr(double shootX, double shootZ, double velX, double velZ, double playerX, double playerZ) {
      return getClosestPointOnFlatLine(shootX, shootZ, velX, velZ, playerX, playerZ).distanceToSqr(playerX, 0.0, playerZ);
   }

   private static double getArrowHitHeight(double gravity, double horizontalVel, double verticalVel, double initialHeight, double distanceTraveled) {
      double time = distanceTraveled / horizontalVel;
      return initialHeight - verticalVel * time - 0.5 * gravity * time * time;
   }

   public static Vec3 calculateArrowClosestApproach(Vec3 shootOrigin, Vec3 shootVelocity, double yGravity, Vec3 playerOrigin) {
      Vec3 flatEncounter = getClosestPointOnFlatLine(shootOrigin.x, shootOrigin.z, shootVelocity.x, shootVelocity.z, playerOrigin.x, playerOrigin.z);
      double encounterDistanceTraveled = flatEncounter.subtract(shootOrigin.x, flatEncounter.y, shootOrigin.z).length();
      double horizontalVel = Math.sqrt(shootVelocity.x * shootVelocity.x + shootVelocity.z * shootVelocity.z);
      double verticalVel = shootVelocity.y;
      double initialHeight = shootOrigin.y;
      double hitHeight = getArrowHitHeight(yGravity, horizontalVel, verticalVel, initialHeight, encounterDistanceTraveled);
      return new Vec3(flatEncounter.x, hitHeight, flatEncounter.z);
   }

   public static Vec3 calculateArrowClosestApproach(CachedProjectile projectile, Vec3 pos) {
      return calculateArrowClosestApproach(projectile.position, projectile.velocity, projectile.gravity, pos);
   }

   public static double[] calculateAnglesForSimpleProjectileMotion(double launchHeight, double launchTargetDistance, double launchVelocity, double gravity) {
      double y = -1.0 * launchHeight;
      double root = launchVelocity * launchVelocity * launchVelocity * launchVelocity
         - gravity * (gravity * launchTargetDistance * launchTargetDistance + 2.0 * y * launchVelocity * launchVelocity);
      if (root < 0.0) {
         Debug.logMessage("Not enough velocity, returning 45 degrees.");
         return new double[]{45.0, 45.0};
      } else {
         double tanTheta0 = (launchVelocity * launchVelocity + Math.sqrt(root)) / gravity * launchTargetDistance;
         double tanTheta1 = (launchVelocity * launchVelocity - Math.sqrt(root)) / gravity * launchTargetDistance;
         double[] angles = new double[]{Math.toDegrees(Math.atan(tanTheta0)), Math.toDegrees(Math.atan(tanTheta1))};
         return new double[]{Math.min(angles[0], angles[1]), Math.max(angles[0], angles[1])};
      }
   }

   public static Vec3 getThrowOrigin(Entity entity) {
      return entity.position().subtract(0.0, 0.1, 0.0);
   }

   @Deprecated
   private static double getNearestTimeOfShotProjectile(Vec3 shootOrigin, Vec3 shootVelocity, double yGravity, Vec3 playerOrigin) {
      Vec3 D = playerOrigin.subtract(shootOrigin);
      double a = yGravity * yGravity / 2.0;
      double b = -(3.0 * yGravity * shootVelocity.y) / 2.0;
      double c = shootVelocity.lengthSqr() + yGravity * shootVelocity.y;
      double d = -1.0 * shootVelocity.dot(D);
      double p = -b / 3.0 * a;
      double q = p * p * p + (b * c - 3.0 * a * d) / 6.0 * a * a;
      double r = c / 3.0 * a;
      double rootInner = q * q + Math.pow(r - p * p, 3.0);
      if (rootInner < 0.0) {
         return -1.0;
      } else {
         rootInner = Math.sqrt(rootInner);
         double outerPreCubeLeft = q + rootInner;
         double outerPreCubeRight = q - rootInner;
         return Math.pow(outerPreCubeLeft, 0.3333333333333333) + Math.pow(outerPreCubeRight, 0.3333333333333333) + p;
      }
   }
}
