package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;

public class CollectFuelTask extends Task {
   private final double targetFuel;

   public CollectFuelTask(double targetFuel) {
      this.targetFuel = targetFuel;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      switch (WorldHelper.getCurrentDimension(this.controller)) {
         case OVERWORLD:
            this.setDebugState("Collecting coal.");
            return TaskCatalogue.getItemTask(Items.COAL, (int)Math.ceil(this.targetFuel / 8.0));
         case END:
            this.setDebugState("Going to overworld, since, well, no more fuel can be found here.");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
         case NETHER:
            this.setDebugState("Going to overworld, since we COULD use wood but wood confuses the bot. A bug at the moment.");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
         default:
            this.setDebugState("INVALID DIMENSION: " + WorldHelper.getCurrentDimension(this.controller));
            return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof CollectFuelTask task ? Math.abs(task.targetFuel - this.targetFuel) < 0.01 : false;
   }

   @Override
   public boolean isFinished() {
      return this.controller.getItemStorage().getItemCountInventoryOnly(Items.COAL) >= this.targetFuel;
   }

   @Override
   protected String toDebugString() {
      return "Collect Fuel: x" + this.targetFuel;
   }
}
