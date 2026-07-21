package adris.altoclef.tasks.misc;

import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.StorageHelper;
import java.util.Arrays;
import net.minecraft.world.item.Item;

public class EquipArmorTask extends Task {
   private final ItemTarget[] toEquip;

   public EquipArmorTask(ItemTarget... toEquip) {
      this.toEquip = toEquip;
   }

   public EquipArmorTask(Item... toEquip) {
      this(Arrays.stream(toEquip).map(ItemTarget::new).toArray(ItemTarget[]::new));
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      ItemTarget[] armorNotPresent = Arrays.stream(this.toEquip)
         .filter(
            targetx -> !this.controller.getItemStorage().hasItem(targetx.getMatches()) && !StorageHelper.isArmorEquipped(this.controller, targetx.getMatches())
         )
         .toArray(ItemTarget[]::new);
      if (armorNotPresent.length > 0) {
         this.setDebugState("Obtaining armor to equip.");
         return new CataloguedResourceTask(armorNotPresent);
      } else {
         this.setDebugState("Equipping armor.");

         for (ItemTarget target : this.toEquip) {
            if (!StorageHelper.isArmorEquipped(this.controller, target.getMatches())) {
               this.controller.getSlotHandler().forceEquipArmor(this.controller, target);
            }
         }

         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return Arrays.stream(this.toEquip).allMatch(target -> StorageHelper.isArmorEquipped(this.controller, target.getMatches()));
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof EquipArmorTask task ? Arrays.equals((Object[])task.toEquip, (Object[])this.toEquip) : false;
   }

   @Override
   protected String toDebugString() {
      return "Equipping armor: " + Arrays.toString((Object[])this.toEquip);
   }
}
