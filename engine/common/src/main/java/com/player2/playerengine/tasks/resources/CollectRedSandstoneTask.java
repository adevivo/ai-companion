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

public class CollectRedSandstoneTask extends ResourceTask {
   private final int count;

   public CollectRedSandstoneTask(int targetCount) {
      super(Items.RED_SANDSTONE, targetCount);
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
      if (mod.getItemStorage().getItemCount(Items.RED_SAND) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.RED_SANDSTONE) + 1;
         ItemTarget s = new ItemTarget(Items.RED_SAND, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.RED_SANDSTONE, target, CraftingRecipe.newShapedRecipe("red_sandstone", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.RED_SANDSTONE, Items.RED_SAND), new Block[]{Blocks.RED_SANDSTONE, Blocks.RED_SAND}, MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectRedSandstoneTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " red sandstone.";
   }
}
