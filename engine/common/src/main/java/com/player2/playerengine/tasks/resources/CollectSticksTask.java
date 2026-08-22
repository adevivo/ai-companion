package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.CraftInInventoryTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.trackers.storage.ItemStorageTracker;
import com.player2.playerengine.util.CraftingRecipe;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.RecipeTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class CollectSticksTask extends ResourceTask {
   private final int targetCount;

   public CollectSticksTask(int targetCount) {
      super(Items.STICK, targetCount);
      this.targetCount = targetCount;
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
      mod.getBehaviour().push();
   }

   @Override
   protected double getPickupRange(PlayerEngineController mod) {
      ItemStorageTracker storage = mod.getItemStorage();
      return storage.getItemCount(ItemHelper.PLANKS) * 4 + storage.getItemCount(ItemHelper.LOG) * 4 * 4 > this.targetCount ? 10.0 : 35.0;
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (mod.getItemStorage().getItemCount(Items.BAMBOO) >= 2) {
         return new CraftInInventoryTask(
            new RecipeTarget(
               Items.STICK,
               Math.min(mod.getItemStorage().getItemCount(Items.BAMBOO) / 2, this.targetCount),
               CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("bamboo"), null, new ItemTarget("bamboo"), null}, 1)
            )
         );
      } else {
         Optional<BlockPos> nearestBush = mod.getBlockScanner().getNearestBlock(Blocks.DEAD_BUSH);
         return (Task)(nearestBush.isPresent() && nearestBush.get().closerToCenterThan(mod.getPlayer().position(), 20.0)
            ? new MineAndCollectTask(Items.DEAD_BUSH, 1, new Block[]{Blocks.DEAD_BUSH}, MiningRequirement.HAND)
            : new CraftInInventoryTask(
               new RecipeTarget(
                  Items.STICK,
                  this.targetCount,
                  CraftingRecipe.newShapedRecipe("sticks", new ItemTarget[]{new ItemTarget("planks"), null, new ItemTarget("planks"), null}, 4)
               )
            ));
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
      mod.getBehaviour().pop();
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectSticksTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Crafting " + this.targetCount + " sticks";
   }
}
