package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.container.CraftInTableTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectHayBlockTask extends ResourceTask {
   private final int count;

   public CollectHayBlockTask(int count) {
      super(Items.HAY_BLOCK, count);
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
      if (mod.getBlockScanner().anyFound(Blocks.HAY_BLOCK)) {
         return new MineAndCollectTask(Items.HAY_BLOCK, this.count, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
      } else {
         ItemTarget w = new ItemTarget(Items.WHEAT, 1);
         return new CraftInTableTask(
            new RecipeTarget(Items.HAY_BLOCK, this.count, CraftingRecipe.newShapedRecipe("hay_block", new ItemTarget[]{w, w, w, w, w, w, w, w, w}, 1))
         );
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectHayBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " hay blocks.";
   }
}
