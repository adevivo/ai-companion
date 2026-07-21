package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.entity.DoToClosestEntityTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasks.movement.GetToYTask;
import adris.altoclef.tasks.movement.ThrowEnderPearlSimpleProjectileTask;
import adris.altoclef.tasks.resources.GetBuildingMaterialsTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class WaitForDragonAndPearlTask extends Task {
   private static final double XZ_RADIUS = 30.0;
   private static final double XZ_RADIUS_TOO_FAR = 38.0;
   private static final int HEIGHT = 42;
   private static final int CLOSE_ENOUGH_DISTANCE = 15;
   private final int Y_COORDINATE = 75;
   private static final double DRAGON_FIREBALL_TOO_CLOSE_RANGE = 40.0;
   private final Task buildingMaterialsTask = new GetBuildingMaterialsTask(52);
   boolean inCenter;
   private Task heightPillarTask;
   private Task throwPearlTask;
   private BlockPos targetToPearl;
   private boolean dragonIsPerching;
   private Task pillarUpFurther;
   private boolean hasPillar = false;

   public void setExitPortalTop(BlockPos top) {
      BlockPos actualTarget = top.below();
      if (!actualTarget.equals(this.targetToPearl)) {
         this.targetToPearl = actualTarget;
         this.throwPearlTask = new ThrowEnderPearlSimpleProjectileTask(actualTarget);
      }
   }

   public void setPerchState(boolean perching) {
      this.dragonIsPerching = perching;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      Optional<Entity> enderMen = mod.getEntityTracker().getClosestEntity(EnderMan.class);
      if (enderMen.isPresent()) {
         EnderMan endermanEntity = (EnderMan)enderMen.get();
         if (endermanEntity instanceof EnderMan && endermanEntity.getTarget() == mod.getPlayer()) {
            this.setDebugState("Killing angry endermen");
            Predicate<Entity> angry = entity -> endermanEntity.getTarget() == mod.getPlayer();
            return new KillEntitiesTask(angry, enderMen.get().getClass());
         }
      }

      if (this.throwPearlTask != null && this.throwPearlTask.isActive() && !this.throwPearlTask.isFinished()) {
         this.setDebugState("Throwing pearl!");
         return this.throwPearlTask;
      } else {
         if (this.pillarUpFurther != null
            && this.pillarUpFurther.isActive()
            && !this.pillarUpFurther.isFinished()
            && mod.getEntityTracker().getClosestEntity(AreaEffectCloud.class).isPresent()) {
            Optional<Entity> cloud = mod.getEntityTracker().getClosestEntity(AreaEffectCloud.class);
            if (cloud.isPresent() && cloud.get().closerThan(mod.getPlayer(), 4.0)) {
               this.setDebugState("PILLAR UP FURTHER to avoid dragon's breath");
               return this.pillarUpFurther;
            }

            Optional<Entity> fireball = mod.getEntityTracker().getClosestEntity(DragonFireball.class);
            if (this.isFireballDangerous(mod, fireball)) {
               this.setDebugState("PILLAR UP FURTHER to avoid dragon's breath");
               return this.pillarUpFurther;
            }
         }

         if (!mod.getItemStorage().hasItem(Items.ENDER_PEARL) && this.inCenter) {
            this.setDebugState("First get ender pearls.");
            return TaskCatalogue.getItemTask(Items.ENDER_PEARL, 1);
         } else {
            int minHeight = this.targetToPearl.getY() + 42 - 3;
            int deltaY = minHeight - mod.getPlayer().blockPosition().getY();
            if (StorageHelper.getBuildingMaterialCount(this.controller) >= Math.min(deltaY - 10, 37)
               && (!this.buildingMaterialsTask.isActive() || this.buildingMaterialsTask.isFinished())) {
               if (this.dragonIsPerching && this.canThrowPearl(mod)) {
                  Debug.logMessage("THROWING PEARL!!");
                  return this.throwPearlTask;
               } else if (mod.getPlayer().blockPosition().getY() < minHeight) {
                  if (mod.getEntityTracker().entityFound(entity -> mod.getPlayer().position().closerThan(entity.position(), 4.0), AreaEffectCloud.class)) {
                     if (mod.getEntityTracker().getClosestEntity(EnderDragon.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
                        LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EnderDragon.class).get().getEyePosition());
                     }

                     return null;
                  } else if (this.heightPillarTask != null && this.heightPillarTask.isActive() && !this.heightPillarTask.isFinished()) {
                     this.setDebugState("Pillaring up!");
                     this.inCenter = true;
                     return (Task)(mod.getEntityTracker().entityFound(EndCrystal.class) ? new DoToClosestEntityTask(toDestroy -> {
                        if (toDestroy.closerThan(mod.getPlayer(), 7.0)) {
                           mod.getControllerExtras().attack(toDestroy);
                        }

                        if (mod.getPlayer().blockPosition().getY() < minHeight) {
                           return this.heightPillarTask;
                        } else {
                           if (mod.getEntityTracker().getClosestEntity(EnderDragon.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
                              LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EnderDragon.class).get().getEyePosition());
                           }

                           return null;
                        }
                     }, EndCrystal.class) : this.heightPillarTask);
                  } else if (!WorldHelper.inRangeXZ(mod.getPlayer(), this.targetToPearl, 38.0) && mod.getPlayer().position().y() < minHeight && !this.hasPillar
                     )
                   {
                     if (mod.getEntityTracker().entityFound(entity -> mod.getPlayer().position().closerThan(entity.position(), 4.0), AreaEffectCloud.class)) {
                        if (mod.getEntityTracker().getClosestEntity(EnderDragon.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
                           LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EnderDragon.class).get().getEyePosition());
                        }

                        return null;
                     } else {
                        this.setDebugState("Moving in (too far, might hit pillars)");
                        return new GetToXZTask(0, 0);
                     }
                  } else {
                     if (!this.hasPillar) {
                        this.hasPillar = true;
                     }

                     this.heightPillarTask = new GetToBlockTask(new BlockPos(0, minHeight, 75));
                     return this.heightPillarTask;
                  }
               } else {
                  this.setDebugState("We're high enough.");
                  Optional<Entity> dragonFireball = mod.getEntityTracker().getClosestEntity(DragonFireball.class);
                  if (dragonFireball.isPresent()
                     && dragonFireball.get().closerThan(mod.getPlayer(), 40.0)
                     && LookHelper.cleanLineOfSight(mod.getPlayer(), dragonFireball.get().position(), 40.0)) {
                     this.pillarUpFurther = new GetToYTask(mod.getPlayer().getBlockY() + 5);
                     Debug.logMessage("HOLDUP");
                     return this.pillarUpFurther;
                  } else if (mod.getEntityTracker().entityFound(EndCrystal.class)) {
                     return new DoToClosestEntityTask(toDestroy -> {
                        if (toDestroy.closerThan(mod.getPlayer(), 7.0)) {
                           mod.getControllerExtras().attack(toDestroy);
                        }

                        if (mod.getPlayer().blockPosition().getY() < minHeight) {
                           return this.heightPillarTask;
                        } else {
                           if (mod.getEntityTracker().getClosestEntity(EnderDragon.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
                              LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EnderDragon.class).get().getEyePosition());
                           }

                           return null;
                        }
                     }, EndCrystal.class);
                  } else {
                     if (mod.getEntityTracker().getClosestEntity(EnderDragon.class).isPresent() && !mod.getBaritone().getPathingBehavior().isPathing()) {
                        LookHelper.lookAt(mod, mod.getEntityTracker().getClosestEntity(EnderDragon.class).get().getEyePosition());
                     }

                     return null;
                  }
               }
            } else {
               this.setDebugState("Collecting building materials...");
               return this.buildingMaterialsTask;
            }
         }
      }
   }

   private boolean canThrowPearl(AltoClefController mod) {
      Vec3 targetPosition = WorldHelper.toVec3d(this.targetToPearl.above());
      BlockHitResult hitResult = LookHelper.raycast(mod.getPlayer(), LookHelper.getCameraPos(mod.getPlayer()), targetPosition, 300.0);
      if (hitResult == null) {
         return true;
      } else {
         return switch (hitResult.getType()) {
            case MISS -> true;
            case BLOCK -> hitResult.getBlockPos().closerThan(this.targetToPearl.above(), 10.0);
            case ENTITY -> false;
            default -> throw new IncompatibleClassChangeError();
         };
      }
   }

   private boolean isFireballDangerous(AltoClefController mod, Optional<Entity> fireball) {
      if (fireball.isEmpty()) {
         return false;
      } else {
         boolean fireballTooClose = fireball.get().closerThan(mod.getPlayer(), 40.0);
         boolean fireballInSight = LookHelper.cleanLineOfSight(mod.getPlayer(), fireball.get().position(), 40.0);
         return fireballTooClose && fireballInSight;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof WaitForDragonAndPearlTask;
   }

   @Override
   public boolean isFinished() {
      return this.dragonIsPerching
         && (
            this.throwPearlTask == null
               || this.throwPearlTask.isActive() && this.throwPearlTask.isFinished()
               || WorldHelper.inRangeXZ(this.controller.getPlayer(), this.targetToPearl, 15.0)
         );
   }

   @Override
   protected String toDebugString() {
      return "Waiting for Dragon Perch + Pearling";
   }
}
