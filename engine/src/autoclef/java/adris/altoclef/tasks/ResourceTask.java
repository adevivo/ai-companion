package adris.altoclef.tasks;

import adris.altoclef.AltoClefController;
import adris.altoclef.BotBehaviour;
import adris.altoclef.multiversion.blockpos.BlockPosVer;
import adris.altoclef.tasks.container.PickupFromContainerTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.resources.MineAndCollectTask;
import adris.altoclef.tasksystem.ITaskCanForce;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ContainerCache;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StlHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.ArrayUtils;

public abstract class ResourceTask extends Task implements ITaskCanForce {
   protected final ItemTarget[] itemTargets;
   private final PickupDroppedItemTask pickupTask;
   private Block[] mineIfPresent = null;
   private BlockPos mineLastClosest = null;
   private boolean forceDimension = false;
   private Dimension targetDimension;
   private ContainerCache currentContainer;
   protected boolean allowContainers = false;

   public ResourceTask(ItemTarget... itemTargets) {
      this.itemTargets = itemTargets;
      this.pickupTask = new PickupDroppedItemTask(this.itemTargets, true);
   }

   public ResourceTask(Item item, int targetCount) {
      this(new ItemTarget(item, targetCount));
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.itemTargetsMet(this.controller, this.itemTargets);
   }

   @Override
   public boolean shouldForce(Task interruptingCandidate) {
      if (StorageHelper.itemTargetsMet(this.controller, this.itemTargets) && !this.isFinished()) {
         ItemStack cursorStack = this.controller.getSlotHandler().getCursorStack();
         return Arrays.stream(this.itemTargets).anyMatch(target -> target.matches(cursorStack.getItem()));
      } else {
         return false;
      }
   }

   @Override
   protected void onStart() {
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.addProtectedItems(ItemTarget.getMatches(this.itemTargets));
      this.onResourceStart(this.controller);
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.isFinished()) {
         return null;
      } else {
         if (!this.shouldAvoidPickingUp(mod) && mod.getEntityTracker().itemDropped(this.itemTargets)) {
            if (PickupDroppedItemTask.isIsGettingPickaxeFirst(mod)) {
               if (this.pickupTask.isCollectingPickaxeForThis()) {
                  this.setDebugState("Picking up (pickaxe first!)");
                  return this.pickupTask;
               }

               Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), this.itemTargets);
               if (closest.isPresent() && !closest.get().closerThan(mod.getPlayer(), 10.0)) {
                  return this.onResourceTick(mod);
               }
            }

            double range = this.getPickupRange(mod);
            Optional<ItemEntity> closest = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), this.itemTargets);
            if (range < 0.0
               || closest.isPresent() && closest.get().closerThan(mod.getPlayer(), range)
               || this.pickupTask.isActive() && !this.pickupTask.isFinished()) {
               this.setDebugState("Picking up");
               return this.pickupTask;
            }
         }

         if (this.currentContainer == null && this.allowContainers) {
            List<ContainerCache> containersWithItem = mod.getItemStorage()
               .getContainersWithItem(
                  Arrays.stream(this.itemTargets)
                     .reduce(new Item[0], (items, target) -> (Item[])ArrayUtils.addAll(items, target.getMatches()), ArrayUtils::addAll)
               );
            if (!containersWithItem.isEmpty()) {
               ContainerCache closest = containersWithItem.stream()
                  .min(StlHelper.compareValues(container -> BlockPosVer.getSquaredDistance(container.getBlockPos(), mod.getPlayer().position())))
                  .get();
               if (closest.getBlockPos()
                  .closerThan(
                     new Vec3i((int)mod.getPlayer().position().x, (int)mod.getPlayer().position().y, (int)mod.getPlayer().position().z),
                     mod.getModSettings().getResourceChestLocateRange()
                  )) {
                  this.currentContainer = closest;
               }
            }
         }

         if (this.currentContainer != null) {
            Optional<ContainerCache> container = mod.getItemStorage().getContainerAtPosition(this.currentContainer.getBlockPos());
            if (container.isPresent()) {
               if (!Arrays.stream(this.itemTargets).noneMatch(target -> container.get().hasItem(target.getMatches()))) {
                  this.setDebugState("Picking up from container");
                  return new PickupFromContainerTask(this.currentContainer.getBlockPos(), this.itemTargets);
               }

               this.currentContainer = null;
            } else {
               this.currentContainer = null;
            }
         }

         if (this.mineIfPresent != null) {
            ArrayList<Block> satisfiedReqs = new ArrayList<>(Arrays.asList(this.mineIfPresent));
            satisfiedReqs.removeIf(block -> !StorageHelper.miningRequirementMet(mod, MiningRequirement.getMinimumRequirementForBlock(block)));
            if (!satisfiedReqs.isEmpty() && mod.getBlockScanner().anyFound(satisfiedReqs.toArray(Block[]::new))) {
               Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(this.mineIfPresent);
               if (closest.isPresent()
                  && closest.get()
                     .closerThan(
                        new Vec3i((int)mod.getPlayer().position().x, (int)mod.getPlayer().position().y, (int)mod.getPlayer().position().z),
                        mod.getModSettings().getResourceMineRange()
                     )) {
                  this.mineLastClosest = closest.get();
               }

               if (this.mineLastClosest != null
                  && this.mineLastClosest
                     .closerThan(
                        new Vec3i((int)mod.getPlayer().position().x, (int)mod.getPlayer().position().y, (int)mod.getPlayer().position().z),
                        mod.getModSettings().getResourceMineRange() * 1.5 + 20.0
                     )) {
                  return new MineAndCollectTask(this.itemTargets, this.mineIfPresent, MiningRequirement.HAND);
               }
            }
         }

         if (this.isInWrongDimension(this.controller)) {
            this.setDebugState("Traveling to correct dimension");
            return this.getToCorrectDimensionTask(this.controller);
         } else {
            return this.onResourceTick(this.controller);
         }
      }
   }

   private boolean isPickupTaskValid(AltoClefController controller) {
      double range = this.getPickupRange(controller);
      return range < 0.0
         ? true
         : controller.getEntityTracker()
            .getClosestItemDrop(controller.getEntity().position(), this.itemTargets)
            .map(itemEntity -> itemEntity.closerThan(controller.getEntity(), range) || this.pickupTask.isActive() && !this.pickupTask.isFinished())
            .orElse(false);
   }

   protected double getPickupRange(AltoClefController controller) {
      return controller.getModSettings().getResourcePickupRange();
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
      this.onResourceStop(this.controller, interruptTask);
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof ResourceTask task)
         ? false
         : Arrays.equals((Object[])task.itemTargets, (Object[])this.itemTargets) && this.isEqualResource(task);
   }

   @Override
   protected String toDebugString() {
      return this.toDebugStringName() + ": " + Arrays.toString((Object[])this.itemTargets);
   }

   protected boolean isInWrongDimension(AltoClefController controller) {
      return this.forceDimension ? WorldHelper.getCurrentDimension(controller) != this.targetDimension : false;
   }

   protected Task getToCorrectDimensionTask(AltoClefController controller) {
      return new DefaultGoToDimensionTask(this.targetDimension);
   }

   public ResourceTask forceDimension(Dimension dimension) {
      this.forceDimension = true;
      this.targetDimension = dimension;
      return this;
   }

   public ItemTarget[] getItemTargets() {
      return this.itemTargets;
   }

   public ResourceTask mineIfPresent(Block[] toMine) {
      this.mineIfPresent = toMine;
      return this;
   }

   protected abstract boolean shouldAvoidPickingUp(AltoClefController var1);

   protected abstract void onResourceStart(AltoClefController var1);

   protected abstract Task onResourceTick(AltoClefController var1);

   protected abstract void onResourceStop(AltoClefController var1, Task var2);

   protected abstract boolean isEqualResource(ResourceTask var1);

   protected abstract String toDebugStringName();
}
