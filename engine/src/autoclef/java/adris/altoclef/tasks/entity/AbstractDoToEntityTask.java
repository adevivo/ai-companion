package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.speedrun.beatgame.BeatMinecraftTask;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import adris.altoclef.util.slots.Slot;
import baritone.api.pathing.goals.GoalRunAway;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;

public abstract class AbstractDoToEntityTask extends Task implements ITaskRequiresGrounded {
   protected final MovementProgressChecker progress = new MovementProgressChecker();
   private final double maintainDistance;
   private final double combatGuardLowerRange;
   private final double combatGuardLowerFieldRadius;
   private TimeoutWanderTask wanderTask;

   protected AbstractDoToEntityTask(double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      this.maintainDistance = maintainDistance;
      this.combatGuardLowerRange = combatGuardLowerRange;
      this.combatGuardLowerFieldRadius = combatGuardLowerFieldRadius;
   }

   protected AbstractDoToEntityTask(double maintainDistance) {
      this(maintainDistance, 0.0, Double.POSITIVE_INFINITY);
   }

   protected AbstractDoToEntityTask(double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      this(-1.0, combatGuardLowerRange, combatGuardLowerFieldRadius);
   }

   @Override
   protected void onStart() {
      AltoClefController mod = this.controller;
      this.progress.reset();
      ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
      if (!cursorStack.isEmpty()) {
         Optional<Slot> moveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursorStack, false);
         moveTo.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP));
         if (ItemHelper.canThrowAwayStack(mod, cursorStack)) {
            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
         }

         Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
         garbage.ifPresent(slot -> mod.getSlotHandler().clickSlot(slot, 0, ClickType.PICKUP));
         mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
      }
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progress.reset();
      }

      Optional<Entity> checkEntity = this.getEntityTarget(mod);
      if (checkEntity.isEmpty()) {
         mod.getMobDefenseChain().resetTargetEntity();
         mod.getMobDefenseChain().resetForceField();
      } else {
         mod.getMobDefenseChain().setTargetEntity(checkEntity.get());
      }

      if (checkEntity.isPresent()) {
         Entity entity = checkEntity.get();
         double playerReach = mod.getModSettings().getEntityReachRange();
         EntityHitResult result = LookHelper.raycast(mod.getPlayer(), entity, playerReach);
         double sqDist = entity.distanceToSqr(mod.getPlayer());
         if (sqDist < this.combatGuardLowerRange * this.combatGuardLowerRange) {
            mod.getMobDefenseChain().setForceFieldRange(this.combatGuardLowerFieldRadius);
         } else {
            mod.getMobDefenseChain().resetForceField();
         }

         double maintainDistance = this.maintainDistance >= 0.0 ? this.maintainDistance : playerReach - 1.0;
         boolean tooClose = sqDist < maintainDistance * maintainDistance;
         if (tooClose && !mod.getBaritone().getCustomGoalProcess().isActive()) {
            mod.getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalRunAway(maintainDistance, entity.blockPosition()));
         }

         if (mod.getControllerExtras().inRange(entity)
            && result != null
            && result.getType() == Type.ENTITY
            && !mod.getFoodChain().needsToEat()
            && !mod.getMLGBucketChain().isFalling(mod)
            && mod.getMLGBucketChain().doneMLG()
            && !mod.getMLGBucketChain().isChorusFruiting()
            && mod.getBaritone().getPathingBehavior().isSafeToCancel()
            && mod.getPlayer().onGround()) {
            this.progress.reset();
            return this.onEntityInteract(mod, entity);
         }

         if (!tooClose) {
            this.setDebugState("Approaching target");
            if (!this.progress.check(mod)) {
               this.progress.reset();
               Debug.logMessage("Failed to get to target, blacklisting.");
               mod.getEntityTracker().requestEntityUnreachable(entity);
            }

            return new GetToEntityTask(entity, maintainDistance);
         }
      }

      if (BeatMinecraftTask.isTaskRunning(mod, this.wanderTask)) {
         return this.wanderTask;
      } else if (!mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
         return null;
      } else {
         this.wanderTask = new TimeoutWanderTask();
         return this.wanderTask;
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      if (other instanceof AbstractDoToEntityTask task) {
         if (!this.doubleCheck(task.maintainDistance, this.maintainDistance)) {
            return false;
         } else if (!this.doubleCheck(task.combatGuardLowerFieldRadius, this.combatGuardLowerFieldRadius)) {
            return false;
         } else {
            return !this.doubleCheck(task.combatGuardLowerRange, this.combatGuardLowerRange) ? false : this.isSubEqual(task);
         }
      } else {
         return false;
      }
   }

   private boolean doubleCheck(double a, double b) {
      return Double.isInfinite(a) == Double.isInfinite(b) ? true : Math.abs(a - b) < 0.1;
   }

   @Override
   protected void onStop(Task interruptTask) {
      AltoClefController mod = this.controller;
      mod.getMobDefenseChain().setTargetEntity(null);
      mod.getMobDefenseChain().resetForceField();
   }

   protected abstract boolean isSubEqual(AbstractDoToEntityTask var1);

   protected abstract Task onEntityInteract(AltoClefController var1, Entity var2);

   protected abstract Optional<Entity> getEntityTarget(AltoClefController var1);
}
