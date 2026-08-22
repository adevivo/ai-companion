package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectQuartzTask extends ResourceTask {
   private final int count;

   public CollectQuartzTask(int count) {
      super(Items.QUARTZ, count);
      this.count = count;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (WorldHelper.getCurrentDimension(mod) != Dimension.NETHER) {
         this.setDebugState("Going to nether");
         return new DefaultGoToDimensionTask(Dimension.NETHER);
      } else {
         this.setDebugState("Mining");
         return new MineAndCollectTask(new ItemTarget(Items.QUARTZ, this.count), new Block[]{Blocks.NETHER_QUARTZ_ORE}, MiningRequirement.WOOD);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectQuartzTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " quartz";
   }
}
