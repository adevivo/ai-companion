package adris.altoclef.tasks.misc;

import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import baritone.api.entity.IInventoryProvider;
import java.util.Arrays;
import net.minecraft.world.item.Item;

/**
 * Holds a non-armor item (a tool/weapon such as an axe, pickaxe, or sword) in the main hand.
 *
 * <p>Companion to {@link EquipArmorTask}: armor goes to armor slots via that task, while everything
 * else is wielded here. If none of the requested items are in the inventory yet, they are obtained
 * first via {@link CataloguedResourceTask}; then the best match is force-selected into the main hand
 * using {@link adris.altoclef.control.SlotHandler#forceEquipItem(Item[])}.
 */
public class HoldItemTask extends Task {
   private final ItemTarget[] toHold;

   public HoldItemTask(ItemTarget... toHold) {
      this.toHold = toHold;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      // Obtain the item(s) first if not one is in the inventory yet.
      boolean anyPresent = Arrays.stream(this.toHold)
         .anyMatch(target -> this.controller.getItemStorage().hasItem(target.getMatches()));
      if (!anyPresent) {
         this.setDebugState("Obtaining item to hold.");
         return new CataloguedResourceTask(this.toHold);
      }

      this.setDebugState("Holding item.");
      for (ItemTarget target : this.toHold) {
         if (this.controller.getItemStorage().hasItem(target.getMatches())) {
            this.controller.getSlotHandler().forceEquipItem(target.getMatches());
            break;
         }
      }
      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      Item mainHand = ((IInventoryProvider) this.controller.getEntity())
         .getLivingInventory().getMainHandStack().getItem();
      return Arrays.stream(this.toHold)
         .anyMatch(target -> Arrays.asList(target.getMatches()).contains(mainHand));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof HoldItemTask task && Arrays.equals((Object[]) task.toHold, (Object[]) this.toHold);
   }

   @Override
   protected String toDebugString() {
      return "Holding item: " + Arrays.toString((Object[]) this.toHold);
   }
}
