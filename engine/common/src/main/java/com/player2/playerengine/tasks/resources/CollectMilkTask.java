package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.entity.AbstractDoToEntityTask;
import com.player2.playerengine.tasks.base.Task;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CollectMilkTask extends ResourceTask {
   private final int count;

   public CollectMilkTask(int targetCount) {
      super(Items.MILK_BUCKET, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
         return TaskCatalogue.getItemTask(Items.BUCKET, 1);
      } else {
         return (Task)(!mod.getEntityTracker().entityFound(Cow.class) && this.isInWrongDimension(mod)
            ? this.getToCorrectDimensionTask(mod)
            : new CollectMilkTask.MilkCowTask());
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectMilkTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " milk buckets.";
   }

   static class MilkCowTask extends AbstractDoToEntityTask {
      public MilkCowTask() {
         super(0.0, -1.0, -1.0);
      }

      @Override
      protected boolean isSubEqual(AbstractDoToEntityTask other) {
         return other instanceof CollectMilkTask.MilkCowTask;
      }

      @Override
      protected Task onEntityInteract(PlayerEngineController mod, Entity entity) {
         if (!mod.getItemStorage().hasItem(Items.BUCKET)) {
            Debug.logWarning("Failed to milk cow because you have no bucket.");
            return null;
         } else {
            if (mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
               mod.getInventory().setItem(mod.getInventory().selectedSlot, new ItemStack(Items.MILK_BUCKET));
            }

            return null;
         }
      }

      @Override
      protected Optional<Entity> getEntityTarget(PlayerEngineController mod) {
         return mod.getEntityTracker().getClosestEntity(mod.getPlayer().position(), Cow.class);
      }

      @Override
      protected String toDebugString() {
         return "Milking Cow";
      }
   }
}
