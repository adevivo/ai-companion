package adris.altoclef.tasks.resources;

import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StorageHelper;
import net.minecraft.world.item.Item;

public class GetBuildingMaterialsTask extends Task {
   private final int count;

   public GetBuildingMaterialsTask(int count) {
      this.count = count;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      Item[] throwaways = this.controller.getModSettings().getThrowawayItems(this.controller, true);
      return new MineAndCollectTask(new ItemTarget[]{new ItemTarget(throwaways, this.count)}, MiningRequirement.WOOD);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetBuildingMaterialsTask task ? task.count == this.count : false;
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.getBuildingMaterialCount(this.controller) >= this.count;
   }

   @Override
   protected String toDebugString() {
      return "Collecting " + this.count + " building materials.";
   }
}
