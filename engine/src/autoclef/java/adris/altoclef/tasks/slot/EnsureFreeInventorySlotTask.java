package adris.altoclef.tasks.slot;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.slots.Slot;
import java.util.Optional;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public class EnsureFreeInventorySlotTask extends Task {

   /**
    * Set when every stack is protected, so there is nothing left to throw away.
    *
    * <p>Without it this task returned null forever in that case — never finishing, never failing and
    * never saying anything, so a caller that reached it would idle indefinitely. It was previously
    * unreachable (see {@code PickupDroppedItemTask.getGoalTask}), which is the only reason that never
    * showed up in a session.
    */
   private boolean nothingToDrop = false;

   @Override
   protected void onStart() {
      this.nothingToDrop = false;
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
         // Logged on the transition only: onTick runs every tick, and setDebugState reaches nothing but
         // taskStatus, so this outcome was previously invisible outside the game.
         if (!this.nothingToDrop) {
            Debug.logMessage("Cannot free an inventory slot — every stack held is protected from being "
                  + "thrown away. Nothing more can be collected until something is deposited or given away.");
         }
         this.nothingToDrop = true;
         return null;
      }
   }

   /** True once it is established that no slot can be freed — nothing held is safe to throw away. */
   public boolean isNothingToDrop() {
      return this.nothingToDrop;
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
