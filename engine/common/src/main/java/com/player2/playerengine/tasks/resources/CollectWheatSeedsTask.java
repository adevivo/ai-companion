package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.MiningRequirement;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectWheatSeedsTask extends ResourceTask {
   private final int count;

   public CollectWheatSeedsTask(int count) {
      super(Items.WHEAT_SEEDS, count);
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
      return (Task)(mod.getBlockScanner().anyFound(Blocks.WHEAT)
         ? new CollectCropTask(Items.AIR, 999, Blocks.WHEAT, Items.WHEAT_SEEDS)
         : new MineAndCollectTask(Items.WHEAT_SEEDS, this.count, new Block[]{Blocks.GRASS_BLOCK, Blocks.TALL_GRASS}, MiningRequirement.HAND));
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectWheatSeedsTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " wheat seeds.";
   }
}
