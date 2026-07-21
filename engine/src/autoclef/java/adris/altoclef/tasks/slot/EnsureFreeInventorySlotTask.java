package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import java.util.Optional;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class EnsureFreeInventorySlotTask extends Task {
   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      ItemStack cursorStack = StorageHelper.getItemStackInCursorSlot(this.controller);
      Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
      if (cursorStack.isEmpty() && garbage.isPresent()) {
         mod.getSlotHandler().clickSlot(garbage.get(), 0, ClickType.PICKUP);
         return null;
      } else if (!cursorStack.isEmpty()) {
         LookHelper.randomOrientation(this.controller);
         mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, ClickType.PICKUP);
         return null;
      } else {
         this.setDebugState("All items are protected.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task obj) {
      return obj instanceof EnsureFreeInventorySlotTask;
   }

   @Override
   protected String toDebugString() {
      return "Ensuring inventory is free";
   }
}
