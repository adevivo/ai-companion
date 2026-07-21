package adris.altoclef.tasks.container;

import adris.altoclef.Debug;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

public class PickupFromContainerTask extends Task {
   private final BlockPos containerPos;
   private final ItemTarget[] targets;

   public PickupFromContainerTask(BlockPos targetContainer, ItemTarget... targets) {
      this.containerPos = targetContainer;
      this.targets = targets;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      if (this.isFinished()) {
         return null;
      } else if (!this.containerPos
         .closerThan(
            new Vec3i(
               (int)this.controller.getEntity().position().x, (int)this.controller.getEntity().position().y, (int)this.controller.getEntity().position().z
            ),
            4.5
         )) {
         return new GetToBlockTask(this.containerPos);
      } else if (!(this.controller.getWorld().getBlockEntity(this.containerPos) instanceof RandomizableContainerBlockEntity container)) {
         Debug.logWarning("Block at " + this.containerPos + " is not a lootable container. Stopping.");
         return null;
      } else {
         RandomizableContainerBlockEntity containerInventory = container;
         LivingEntityInventory playerInventory = ((IInventoryProvider)this.controller.getEntity()).getLivingInventory();

         for (ItemTarget target : this.targets) {
            int needed = target.getTargetCount() - this.controller.getItemStorage().getItemCount(target);
            if (needed > 0) {
               for (int i = 0; i < containerInventory.getContainerSize(); i++) {
                  ItemStack stack = containerInventory.getItem(i);
                  if (target.matches(stack.getItem())) {
                     this.setDebugState("Looting " + target);
                     if (!playerInventory.insertStack(new ItemStack(stack.getItem()))) {
                        return new EnsureFreeInventorySlotTask();
                     }

                     ItemStack toMove = stack.copy();
                     int moveAmount = Math.min(toMove.getCount(), needed);
                     toMove.setCount(moveAmount);
                     if (playerInventory.insertStack(toMove)) {
                        stack.shrink(moveAmount);
                        containerInventory.setItem(i, stack);
                        container.setChanged();
                        this.controller.getItemStorage().registerSlotAction();
                     }

                     return null;
                  }
               }
            }
         }

         this.setDebugState("Waiting for items to appear in container or finishing.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return Arrays.stream(this.targets).allMatch(target -> this.controller.getItemStorage().getItemCount(target) >= target.getTargetCount());
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof PickupFromContainerTask task)
         ? false
         : Objects.equals(task.containerPos, this.containerPos) && Arrays.equals((Object[])task.targets, (Object[])this.targets);
   }

   @Override
   protected String toDebugString() {
      return "Picking up from container at (" + this.containerPos.toShortString() + "): " + Arrays.toString((Object[])this.targets);
   }
}
