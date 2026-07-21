package adris.altoclef.tasks.movement;

import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;

public class LocateDesertTempleTask extends Task {
   private BlockPos finalPos;

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      BlockPos desertTemplePos = WorldHelper.getADesertTemple(this.controller);
      if (desertTemplePos != null) {
         this.finalPos = desertTemplePos.above(14);
      }

      if (this.finalPos != null) {
         this.setDebugState("Going to found desert temple");
         return new GetToBlockTask(this.finalPos, false);
      } else {
         return new SearchWithinBiomeTask(Biomes.DESERT);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof LocateDesertTempleTask;
   }

   @Override
   protected String toDebugString() {
      return "Searchin' for temples";
   }

   @Override
   public boolean isFinished() {
      return this.controller.getPlayer().blockPosition().equals(this.finalPos);
   }
}
