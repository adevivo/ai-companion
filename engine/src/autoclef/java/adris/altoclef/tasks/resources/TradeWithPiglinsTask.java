package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.entity.AbstractDoToEntityTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.utils.Debug;
import java.util.HashSet;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class TradeWithPiglinsTask extends ResourceTask {
   private static final boolean AVOID_HOGLINS = true;
   private static final double HOGLIN_AVOID_TRADE_RADIUS = 64.0;
   private static final double TRADING_PIGLIN_TOO_FAR_AWAY = 72.0;
   private final int goldBuffer;
   private final Task tradeTask = new TradeWithPiglinsTask.PerformTradeWithPiglin();
   private Task goldTask = null;

   public TradeWithPiglinsTask(int goldBuffer, ItemTarget[] itemTargets) {
      super(itemTargets);
      this.goldBuffer = goldBuffer;
   }

   public TradeWithPiglinsTask(int goldBuffer, ItemTarget target) {
      super(target);
      this.goldBuffer = goldBuffer;
   }

   public TradeWithPiglinsTask(int goldBuffer, Item item, int targetCount) {
      super(item, targetCount);
      this.goldBuffer = goldBuffer;
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (this.goldTask != null && this.goldTask.isActive() && !this.goldTask.isFinished()) {
         this.setDebugState("Collecting gold");
         return this.goldTask;
      } else if (!mod.getItemStorage().hasItem(Items.GOLD_INGOT)) {
         if (this.goldTask == null) {
            this.goldTask = TaskCatalogue.getItemTask(Items.GOLD_INGOT, this.goldBuffer);
         }

         return this.goldTask;
      } else if (!mod.getEntityTracker().entityFound(Piglin.class)) {
         this.setDebugState("Wandering");
         return new TimeoutWanderTask(false);
      } else {
         this.setDebugState("Trading with Piglin");
         return this.tradeTask;
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof TradeWithPiglinsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Trading with Piglins";
   }

   static class PerformTradeWithPiglin extends AbstractDoToEntityTask {
      private static final double PIGLIN_NEARBY_RADIUS = 10.0;
      private final TimerGame barterTimeout = new TimerGame(2.0);
      private final TimerGame intervalTimeout = new TimerGame(10.0);
      private final HashSet<Entity> blacklisted = new HashSet<>();
      private Entity currentlyBartering = null;

      public PerformTradeWithPiglin() {
         super(3.0);
      }

      @Override
      protected void onStart() {
         super.onStart();
         AltoClefController mod = this.controller;
         mod.getBehaviour().push();
         mod.getBehaviour().addProtectedItems(Items.GOLD_INGOT);
         mod.getBehaviour().addForceFieldExclusion(entity -> entity instanceof Piglin ? !this.blacklisted.contains(entity) : false);
      }

      @Override
      protected void onStop(Task interruptTask) {
         super.onStop(interruptTask);
         this.controller.getBehaviour().pop();
      }

      @Override
      protected boolean isSubEqual(AbstractDoToEntityTask other) {
         return other instanceof TradeWithPiglinsTask.PerformTradeWithPiglin;
      }

      @Override
      protected Task onEntityInteract(AltoClefController mod, Entity entity) {
         if (this.intervalTimeout.elapsed()) {
            this.barterTimeout.reset();
            this.intervalTimeout.reset();
         }

         if (EntityHelper.isTradingPiglin(this.currentlyBartering)) {
            this.barterTimeout.reset();
         }

         if (!entity.equals(this.currentlyBartering)) {
            this.currentlyBartering = entity;
            this.barterTimeout.reset();
         }

         if (this.barterTimeout.elapsed()) {
            Debug.logMessage("Failed bartering with current piglin, blacklisting.");
            this.blacklisted.add(this.currentlyBartering);
            this.barterTimeout.reset();
            this.currentlyBartering = null;
            return null;
         } else {
            if (this.currentlyBartering != null && !EntityHelper.isTradingPiglin(this.currentlyBartering)) {
               Optional<Entity> closestHoglin = mod.getEntityTracker().getClosestEntity(this.currentlyBartering.position(), Hoglin.class);
               if (closestHoglin.isPresent() && closestHoglin.get().closerThan(entity, 64.0)) {
                  Debug.logMessage("Aborting further trading because a hoglin showed up");
                  this.blacklisted.add(this.currentlyBartering);
                  this.barterTimeout.reset();
                  this.currentlyBartering = null;
               }
            }

            this.setDebugState("Trading with piglin");
            if (mod.getSlotHandler().forceEquipItem(Items.GOLD_INGOT)) {
               Debug.logError("NYI");
               this.intervalTimeout.reset();
            }

            return null;
         }
      }

      @Override
      protected Optional<Entity> getEntityTarget(AltoClefController mod) {
         Optional<Entity> found = mod.getEntityTracker()
            .getClosestEntity(
               mod.getPlayer().position(),
               entity -> {
                  if (!this.blacklisted.contains(entity)
                     && !EntityHelper.isTradingPiglin(entity)
                     && (!(entity instanceof LivingEntity) || !((LivingEntity)entity).isBaby())
                     && (this.currentlyBartering == null || entity.closerThan(this.currentlyBartering, 10.0))) {
                     Optional<Entity> closestHoglin = mod.getEntityTracker().getClosestEntity(entity.position(), Hoglin.class);
                     return closestHoglin.isEmpty() || !closestHoglin.get().closerThan(entity, 64.0);
                  } else {
                     return false;
                  }
               },
               Piglin.class
            );
         if (found.isEmpty()) {
            if (this.currentlyBartering != null && (this.blacklisted.contains(this.currentlyBartering) || !this.currentlyBartering.isAlive())) {
               this.currentlyBartering = null;
            }

            found = Optional.ofNullable(this.currentlyBartering);
         }

         return found;
      }

      @Override
      protected String toDebugString() {
         return "Trading with piglin";
      }
   }
}
