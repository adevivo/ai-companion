package com.player2.playerengine.tasks.container;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.mixins.MixinAbstractFurnaceBlockEntity;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.tasks.movement.GetCloseToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.resources.CollectFuelTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.SmeltTarget;
import com.player2.playerengine.util.helpers.StorageHelper;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.automaton.api.entity.LivingEntityInventory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class SmeltInSmokerTask extends ResourceTask {
   private final SmeltTarget[] targets;
   private final TimerGame smeltTimer = new TimerGame(5.0);
   private BlockPos smokerPos = null;
   private boolean isSmelting = false;
   private SmeltInSmokerTask.SmokerCache cache;

   public SmeltInSmokerTask(SmeltTarget... targets) {
      super(extractItemTargets(targets));
      this.targets = targets;
   }

   public SmeltInSmokerTask(SmeltTarget target) {
      this(new SmeltTarget[]{target});
   }

   private static ItemTarget[] extractItemTargets(SmeltTarget[] recipeTargets) {
      List<ItemTarget> result = new ArrayList<>(recipeTargets.length);

      for (SmeltTarget target : recipeTargets) {
         result.add(target.getItem());
      }

      return result.toArray(ItemTarget[]::new);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController controller) {
      controller.getBehaviour().push();
      controller.getBehaviour().addProtectedItems(Items.SMOKER);

      for (SmeltTarget target : this.targets) {
         controller.getBehaviour().addProtectedItems(target.getMaterial().getMatches());
      }
   }

   @Override
   protected Task onResourceTick(PlayerEngineController controller) {
      boolean allDone = Arrays.stream(this.targets)
         .allMatch(target -> controller.getItemStorage().getItemCount(target.getItem()) >= target.getItem().getTargetCount());
      if (allDone) {
         this.setDebugState("Done smoking.");
         return null;
      } else {
         SmeltTarget currentTarget = Arrays.stream(this.targets)
            .filter(t -> controller.getItemStorage().getItemCount(t.getItem()) < t.getItem().getTargetCount())
            .findFirst()
            .orElse(null);
         if (currentTarget == null) {
            return null;
         } else {
            this.smeltTimer.setInterval(10 * currentTarget.getItem().getTargetCount());
            int fuelNeeded = (int)Math.ceil(currentTarget.getItem().getTargetCount() / 8.0);
            if (!this.isSmelting) {
               if (!controller.getItemStorage().hasItem(currentTarget.getMaterial())) {
                  this.setDebugState("Collecting raw food: " + currentTarget.getMaterial());
                  return TaskCatalogue.getItemTask(currentTarget.getMaterial());
               }

               if (StorageHelper.calculateInventoryFuelCount(controller) < fuelNeeded) {
                  this.setDebugState("Collecting fuel.");
                  return new CollectFuelTask(fuelNeeded);
               }
            }

            if (this.smokerPos == null || !controller.getWorld().getBlockState(this.smokerPos).is(Blocks.SMOKER)) {
               Optional<BlockPos> nearestSmoker = controller.getBlockScanner().getNearestBlock(Blocks.SMOKER);
               if (!nearestSmoker.isPresent()) {
                  if (controller.getItemStorage().hasItem(Items.SMOKER)) {
                     this.setDebugState("Placing smoker.");
                     return new PlaceBlockNearbyTask(Blocks.SMOKER);
                  }

                  this.setDebugState("Obtaining smoker.");
                  return TaskCatalogue.getItemTask(Items.SMOKER, 1);
               }

               this.smokerPos = nearestSmoker.get();
            }

            if (!this.smokerPos
               .closerThan(
                  new Vec3i((int)controller.getEntity().position().x, (int)controller.getEntity().position().y, (int)controller.getEntity().position().z), 4.5
               )) {
               this.setDebugState("Going to smoker.");
               return new GetCloseToBlockTask(this.smokerPos);
            } else if (controller.getWorld().getBlockEntity(this.smokerPos) instanceof AbstractFurnaceBlockEntity smoker) {
               ItemStack outputStack = smoker.getItem(2);
               if (!outputStack.isEmpty()) {
                  this.setDebugState("Taking smoked items.");
                  LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
                  if (!playerInv.insertStack(outputStack)) {
                     this.setDebugState("Inventory full.");
                     return null;
                  }

                  smoker.setItem(2, ItemStack.EMPTY);
                  smoker.setChanged();
               }

               if (this.isSmelting) {
                  this.setDebugState("Waiting for items to smoke...");
                  if (this.smeltTimer.elapsed()) {
                     this.isSmelting = false;
                  }

                  return null;
               } else {
                  LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
                  if (((MixinAbstractFurnaceBlockEntity)smoker).getPropertyDelegate().get(0) <= 1 && smoker.getItem(1).isEmpty()) {
                     this.setDebugState("Adding fuel.");
                     Item fuelItem = controller.getModSettings().getSupportedFuelItems()[0];
                     int fuelSlotIndex = playerInv.getSlotWithStack(new ItemStack(fuelItem));
                     if (fuelSlotIndex != -1) {
                        smoker.setItem(1, playerInv.removeItem(fuelSlotIndex, fuelNeeded));
                        smoker.setChanged();
                     }
                  }

                  if (smoker.getItem(0).isEmpty()) {
                     this.setDebugState("Adding raw food.");
                     Item materialItem = currentTarget.getMaterial().getMatches()[0];
                     int materialSlotIndex = playerInv.getSlotWithStack(new ItemStack(materialItem));
                     if (materialSlotIndex != -1) {
                        smoker.setItem(0, playerInv.removeItem(materialSlotIndex, currentTarget.getMaterial().getTargetCount()));
                        this.isSmelting = true;
                        this.smeltTimer.reset();
                        smoker.setChanged();
                        return null;
                     }
                  }

                  this.isSmelting = true;
                  this.smeltTimer.reset();
                  this.setDebugState("Waiting for smoker...");
                  return null;
               }
            } else {
               Debug.logWarning("Block at smoker position is not a smoker BE. Resetting.");
               this.smokerPos = null;
               return new TimeoutWanderTask(1.0F);
            }
         }
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController controller, Task interruptTask) {
      controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof SmeltInSmokerTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Smelting in Smoker";
   }

   static class SmokerCache {
      public ItemStack materialSlot = ItemStack.EMPTY;
      public ItemStack fuelSlot = ItemStack.EMPTY;
      public ItemStack outputSlot = ItemStack.EMPTY;
      public double burningFuelCount;
      public double burnPercentage;
   }
}
