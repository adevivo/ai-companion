package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.StorageHelper;
import net.minecraft.world.item.Items;

public class SatisfyMiningRequirementTask extends Task {
   private final MiningRequirement requirement;

   public SatisfyMiningRequirementTask(MiningRequirement requirement) {
      this.requirement = requirement;
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      switch (this.requirement) {
         case HAND:
         default:
            return null;
         case WOOD:
            return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
         case STONE:
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
         case IRON:
            return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
         case DIAMOND:
            return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof SatisfyMiningRequirementTask task ? task.requirement == this.requirement : false;
   }

   @Override
   protected String toDebugString() {
      return "Satisfy Mining Req: " + this.requirement;
   }

   @Override
   public boolean isFinished() {
      return StorageHelper.miningRequirementMetInventory(this.controller, this.requirement);
   }
}
