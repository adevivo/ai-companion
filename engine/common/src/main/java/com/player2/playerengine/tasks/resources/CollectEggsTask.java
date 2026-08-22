package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.entity.DoToClosestEntityTask;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.Items;

public class CollectEggsTask extends ResourceTask {
   private final int count;
   private final DoToClosestEntityTask waitNearChickens;
   private PlayerEngineController mod;

   public CollectEggsTask(int targetCount) {
      super(Items.EGG, targetCount);
      this.count = targetCount;
      this.waitNearChickens = new DoToClosestEntityTask(chicken -> new GetToEntityTask(chicken, 5.0), Chicken.class);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      this.mod = mod;
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (this.waitNearChickens.wasWandering() && WorldHelper.getCurrentDimension(this.controller) != Dimension.OVERWORLD) {
         this.setDebugState("Going to right dimension.");
         return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
      } else {
         this.setDebugState("Waiting around chickens. Yes.");
         return this.waitNearChickens;
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
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
