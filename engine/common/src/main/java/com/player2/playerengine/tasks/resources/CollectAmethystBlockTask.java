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
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class CollectAmethystBlockTask extends ResourceTask {
   private final int count;

   public CollectAmethystBlockTask(int targetCount) {
      super(Items.AMETHYST_BLOCK, targetCount);
      this.count = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      mod.getBehaviour().push();
      mod.getBehaviour().avoidBlockBreaking((Predicate<BlockPos>)(blockPos -> {
         BlockState s = mod.getWorld().getBlockState(blockPos);
         return s.getBlock() == Blocks.BUDDING_AMETHYST;
      }));
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (mod.getItemStorage().getItemCount(Items.AMETHYST_SHARD) >= 4) {
         int target = mod.getItemStorage().getItemCount(Items.AMETHYST_BLOCK) + 1;
         ItemTarget s = new ItemTarget(Items.AMETHYST_SHARD, 1);
         return new CraftInInventoryTask(
            new RecipeTarget(Items.AMETHYST_BLOCK, target, CraftingRecipe.newShapedRecipe("amethyst_block", new ItemTarget[]{s, s, s, s}, 1))
         );
      } else {
         return new MineAndCollectTask(
               new ItemTarget(Items.AMETHYST_BLOCK, Items.AMETHYST_SHARD), new Block[]{Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_CLUSTER}, MiningRequirement.WOOD
            )
            .forceDimension(Dimension.OVERWORLD);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectAmethystBlockTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " Amethyst Blocks.";
   }
}
