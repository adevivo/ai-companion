package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectSandstoneTask extends ResourceTask {
   private final int count;

   public CollectSandstoneTask(int targetCount) {
      super(Items.SANDSTONE, targetCount);
      this.count = targetCount;
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
      if (mod.getItemStorage().getItemCount(Items.SAND) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.SANDSTONE) + 1;
         ItemTarget s = new ItemTarget(Items.SAND, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.SANDSTONE, target, CraftingRecipe.newShapedRecipe("sandstone", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(new ItemTarget(Items.SANDSTONE, Items.SAND), new Block[]{Blocks.SANDSTONE, Blocks.SAND}, MiningRequirement.WOOD)
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectSandstoneTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " sandstone.";
   }
}
