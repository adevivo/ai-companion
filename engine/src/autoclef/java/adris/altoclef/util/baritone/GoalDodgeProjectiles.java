package adris.altoclef.util.baritone;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.util.helpers.BaritoneHelper;
import adris.altoclef.util.helpers.ProjectileHelper;
import baritone.api.pathing.goals.Goal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class GoalDodgeProjectiles implements Goal {
   private static final double Y_SCALE = 0.3F;
   private final AltoClefController mod;
   private final double distanceHorizontal;
   private final double distanceVertical;
   private final List<CachedProjectile> cachedProjectiles = new ArrayList<>();

   public GoalDodgeProjectiles(AltoClefController mod, double distanceHorizontal, double distanceVertical) {
      this.mod = mod;
      this.distanceHorizontal = distanceHorizontal;
      this.distanceVertical = distanceVertical;
   }

   private static boolean isInvalidProjectile(CachedProjectile projectile) {
      return projectile == null;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      List<CachedProjectile> projectiles = this.getProjectiles();
      Vec3 p = new Vec3(x, y, z);
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         if (!projectiles.isEmpty()) {
            for (CachedProjectile projectile : projectiles) {
               if (!isInvalidProjectile(projectile)) {
                  try {
                     if (projectile.needsToRecache()) {
                        projectile.setCacheHit(ProjectileHelper.calculateArrowClosestApproach(projectile, p));
                     }

                     Vec3 hit = projectile.getCachedHit();
                     if (this.isHitCloseEnough(hit, p)) {
                        return false;
                     }
                  } catch (Exception var11) {
                     Debug.logWarning("Weird exception caught while checking for goal: " + var11.getMessage());
                  }
               }
            }
         }

         return true;
      }
   }

   @Override
   public double heuristic(int x, int y, int z) {
      Vec3 p = new Vec3(x, y, z);
      double costFactor = 0.0;
      List<CachedProjectile> projectiles = this.getProjectiles();
      synchronized (BaritoneHelper.MINECRAFT_LOCK) {
         if (!projectiles.isEmpty()) {
            for (CachedProjectile projectile : projectiles) {
               if (!isInvalidProjectile(projectile)) {
                  if (projectile.needsToRecache()) {
                     projectile.setCacheHit(ProjectileHelper.calculateArrowClosestApproach(projectile, p));
                  }

                  Vec3 hit = projectile.getCachedHit();
                  double arrowPenalty = ProjectileHelper.getFlatDistanceSqr(
                     projectile.position.x, projectile.position.z, projectile.velocity.x, projectile.velocity.z, p.x, p.z
                  );
                  if (this.isHitCloseEnough(hit, p)) {
                     costFactor += arrowPenalty;
                  }
               }
            }
         }
      }

      return -1.0 * costFactor;
   }

   private boolean isHitCloseEnough(Vec3 hit, Vec3 to) {
      Vec3 delta = to.subtract(hit);
      double horizontalSquared = delta.x * delta.x + delta.z * delta.z;
      double vertical = Math.abs(delta.y);
      return horizontalSquared < this.distanceHorizontal * this.distanceHorizontal && vertical < this.distanceVertical;
   }

   private List<CachedProjectile> getProjectiles() {
      return this.mod.getEntityTracker().getProjectiles();
   }
}
