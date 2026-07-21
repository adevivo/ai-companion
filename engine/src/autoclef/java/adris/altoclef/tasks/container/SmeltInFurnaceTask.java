package adris.altoclef.tasks.container;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.mixins.MixinAbstractFurnaceBlockEntity;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.movement.GetCloseToBlockTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.resources.CollectFuelTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
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

public class SmeltInFurnaceTask extends ResourceTask {
   private final SmeltTarget[] targets;
   private final TimerGame smeltTimer = new TimerGame(10.0);
   private BlockPos furnacePos = null;
   private boolean isSmelting = false;

   public SmeltInFurnaceTask(SmeltTarget... targets) {
      super(extractItemTargets(targets));
      this.targets = targets;
   }

   public SmeltInFurnaceTask(SmeltTarget target) {
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
   protected boolean shouldAvoidPickingUp(AltoClefController controller) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController controller) {
      controller.getBehaviour().push();
      controller.getBehaviour().addProtectedItems(Items.FURNACE);

      for (SmeltTarget target : this.targets) {
         controller.getBehaviour().addProtectedItems(target.getMaterial().getMatches());
      }
   }

   @Override
   protected Task onResourceTick(AltoClefController controller) {
      boolean allDone = Arrays.stream(this.targets)
         .allMatch(target -> controller.getItemStorage().getItemCount(target.getItem()) >= target.getItem().getTargetCount());
      if (allDone) {
         this.setDebugState("Done smelting.");
         return null;
      } else {
         SmeltTarget currentTarget = null;

         for (SmeltTarget target : this.targets) {
            if (controller.getItemStorage().getItemCount(target.getItem()) < target.getItem().getTargetCount()) {
               currentTarget = target;
               break;
            }
         }

         if (currentTarget == null) {
            Debug.logWarning("Smelting task is running, but all targets are met. This should not happen.");
            return null;
         } else {
            this.smeltTimer.setInterval(10 * currentTarget.getItem().getTargetCount());
            int fuelNeeded = (int)Math.ceil(currentTarget.getItem().getTargetCount() / 8.0);
            if (!this.isSmelting) {
               if (controller.getItemStorage().getItemCount(currentTarget.getMaterial()) < currentTarget.getMaterial().getTargetCount()) {
                  this.setDebugState("Collecting materials for smelting: " + currentTarget.getMaterial());
                  return TaskCatalogue.getItemTask(currentTarget.getMaterial());
               }

               if (StorageHelper.calculateInventoryFuelCount(controller) < fuelNeeded) {
                  this.setDebugState("Collecting fuel.");
                  return new CollectFuelTask(fuelNeeded);
               }
            }

            if (this.furnacePos == null || !controller.getWorld().getBlockState(this.furnacePos).is(Blocks.FURNACE)) {
               Optional<BlockPos> nearestFurnace = controller.getBlockScanner().getNearestBlock(Blocks.FURNACE);
               if (nearestFurnace.isPresent()
                  && !nearestFurnace.get()
                     .closerThan(
                        new Vec3i((int)controller.getEntity().position().x, (int)controller.getEntity().position().y, (int)controller.getEntity().position().z),
                        100.0
                     )) {
                  nearestFurnace = Optional.empty();
               }

               if (!nearestFurnace.isPresent()) {
                  if (controller.getItemStorage().hasItem(Items.FURNACE)) {
                     this.setDebugState("Placing furnace.");
                     return new PlaceBlockNearbyTask(Blocks.FURNACE);
                  }

                  this.setDebugState("Obtaining furnace.");
                  return TaskCatalogue.getItemTask(Items.FURNACE, 1);
               }

               this.furnacePos = nearestFurnace.get();
            }

            if (!this.furnacePos
               .closerThan(
                  new Vec3i((int)controller.getEntity().position().x, (int)controller.getEntity().position().y, (int)controller.getEntity().position().z), 4.5
               )) {
               this.setDebugState("Going to furnace.");
               return new GetCloseToBlockTask(this.furnacePos);
            } else if (controller.getWorld().getBlockEntity(this.furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
               ItemStack outputStack = furnace.getItem(2);
               if (!outputStack.isEmpty()) {
                  this.setDebugState("Taking smelted items.");
                  LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
                  if (!playerInv.insertStack(outputStack)) {
                     this.setDebugState("Inventory is full, cannot take smelted items.");
                     return null;
                  }

                  furnace.setItem(2, ItemStack.EMPTY);
                  furnace.setChanged();
               }

               if (this.isSmelting) {
                  this.setDebugState("Waiting for items to smelt...");
                  if (this.smeltTimer.elapsed()) {
                     this.isSmelting = false;
                  }

                  return null;
               } else {
                  ItemStack materialSlot = furnace.getItem(0);
                  ItemStack fuelSlot = furnace.getItem(1);
                  LivingEntityInventory playerInv = ((IInventoryProvider)controller.getEntity()).getLivingInventory();
                  if (((MixinAbstractFurnaceBlockEntity)furnace).getPropertyDelegate().get(0) <= 1 && fuelSlot.isEmpty()) {
                     this.setDebugState("Adding fuel.");
                     Item fuelItem = controller.getModSettings().getSupportedFuelItems()[0];
                     int fuelSlotIndex = playerInv.getSlotWithStack(new ItemStack(fuelItem));
                     if (fuelSlotIndex != -1) {
                        furnace.setItem(1, playerInv.removeItem(fuelSlotIndex, fuelNeeded));
                        furnace.setChanged();
                     }
                  }

                  if (materialSlot.isEmpty()) {
                     this.setDebugState("Adding material.");
                     Item materialItem = currentTarget.getMaterial().getMatches()[0];
                     int materialSlotIndex = playerInv.getSlotWithStack(new ItemStack(materialItem));
                     if (materialSlotIndex != -1) {
                        furnace.setItem(0, playerInv.removeItem(materialSlotIndex, currentTarget.getMaterial().getTargetCount()));
                        this.isSmelting = true;
                        this.smeltTimer.reset();
                        furnace.setChanged();
                        return null;
                     }
                  }

                  this.isSmelting = true;
                  this.smeltTimer.reset();
                  this.setDebugState("Waiting for furnace...");
                  return null;
               }
            } else {
               Debug.logWarning("Block at furnace position is not a furnace BE. Resetting.");
               this.furnacePos = null;
               return new TimeoutWanderTask(1.0F);
            }
         }
      }
   }

   @Override
   protected void onResourceStop(AltoClefController controller, Task interruptTask) {
      controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof SmeltInFurnaceTask task ? Arrays.equals((Object[])task.targets, (Object[])this.targets) : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Smelting in Furnace";
   }
}
