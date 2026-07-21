package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.entity.DoToClosestEntityTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.Items;

public class CollectEggsTask extends ResourceTask {
   private final int count;
   private final DoToClosestEntityTask waitNearChickens;
   private AltoClefController mod;

   public CollectEggsTask(int targetCount) {
      super(Items.EGG, targetCount);
      this.count = targetCount;
      this.waitNearChickens = new DoToClosestEntityTask(chicken -> new GetToEntityTask(chicken, 5.0), Chicken.class);
   }

   @Override
   protected boolean shouldAvoidPickingUp(AltoClefController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(AltoClefController mod) {
      this.mod = mod;
   }

   @Override
   protected Task onResourceTick(AltoClefController mod) {
      if (this.waitNearChickens.wasWandering() && WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         this.setDebugState("Going to right dimension.");
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      } else {
         this.setDebugState("Waiting around chickens. Yes.");
         return this.waitNearChickens;
      }
   }

   @Override
   protected void onResourceStop(AltoClefController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectEggsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " eggs.";
   }
}
