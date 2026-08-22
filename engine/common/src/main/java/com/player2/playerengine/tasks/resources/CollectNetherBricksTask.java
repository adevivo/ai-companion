package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectNetherBricksTask extends ResourceTask {
   private final int count;

   public CollectNetherBricksTask(int count) {
      super(Items.NETHER_BRICKS, count);
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
      if (mod.getBlockScanner().anyFound(Blocks.NETHER_BRICKS)) {
         return new MineAndCollectTask(Items.NETHER_BRICKS, this.count, new Block[]{Blocks.NETHER_BRICKS}, MiningRequirement.WOOD);
      } else {
         ItemTarget b = new ItemTarget(Items.NETHER_BRICK, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.NETHER_BRICK, this.count, CraftingRecipe.newShapedRecipe("nether_brick", new ItemTarget[]{b, b, b, b}, 1))
         );
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectNetherBricksTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " nether bricks.";
   }
}
