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
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectCoarseDirtTask extends ResourceTask {
   private static final float CLOSE_ENOUGH_COARSE_DIRT = 128.0F;
   private final int count;

   public CollectCoarseDirtTask(int targetCount) {
      super(Items.COARSE_DIRT, targetCount);
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
      double c = Math.ceil((this.count - mod.getItemStorage().getItemCount(Items.COARSE_DIRT)) / 4.0) * 2.0;
      Optional<BlockPos> closest = mod.getBlockScanner().getNearestBlock(Blocks.COARSE_DIRT);
      if ((mod.getItemStorage().getItemCount(Items.DIRT) < c || mod.getItemStorage().getItemCount(Items.GRAVEL) < c)
         && closest.isPresent()
         && closest.get().closerToCenterThan(mod.getPlayer().position(), 128.0)) {
         return new MineAndCollectTask(new ItemTarget(Items.COARSE_DIRT), new Block[]{Blocks.COARSE_DIRT}, MiningRequirement.HAND)
            .forceDimension(Dimension.OVERWORLD);
      } else {
         int target = this.count;
         ItemTarget d = new ItemTarget(Items.DIRT, 1);
         ItemTarget g = new ItemTarget(Items.GRAVEL, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.COARSE_DIRT, target, CraftingRecipe.newShapedRecipe("coarse_dirt", new ItemTarget[]{d, g, g, d}, 4))
         );
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectCoarseDirtTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Coarse Dirt.";
   }
}
