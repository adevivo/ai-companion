package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.DoToClosestBlockTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.movement.SearchWithinBiomeTask;
import com.player2.playerengine.tasks.base.Task;
import java.util.HashSet;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;

public class CollectCocoaBeansTask extends ResourceTask {
   private final int count;
   private final HashSet<BlockPos> wasFullyGrown = new HashSet<>();

   public CollectCocoaBeansTask(int targetCount) {
      super(Items.COCOA_BEANS, targetCount);
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
      Predicate<BlockPos> validCocoa = blockPos -> {
         if (!mod.getChunkTracker().isChunkLoaded(blockPos)) {
            return this.wasFullyGrown.contains(blockPos);
         } else {
            BlockState s = mod.getWorld().getBlockState(blockPos);
            boolean mature = (Integer)s.getValue(CocoaBlock.AGE) == 2;
            if (this.wasFullyGrown.contains(blockPos)) {
               if (!mature) {
                  this.wasFullyGrown.remove(blockPos);
               }
            } else if (mature) {
               this.wasFullyGrown.add(blockPos);
            }

            return mature;
         }
      };
      if (mod.getBlockScanner().anyFound(validCocoa, Blocks.COCOA)) {
         this.setDebugState("Breaking cocoa blocks");
         return new DoToClosestBlockTask(DestroyBlockTask::new, validCocoa, Blocks.COCOA);
      } else if (this.isInWrongDimension(mod)) {
         return this.getToCorrectDimensionTask(mod);
      } else {
         this.setDebugState("Exploring around jungles");
         return new SearchWithinBiomeTask(Biomes.JUNGLE);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectCocoaBeansTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collecting " + this.count + " cocoa beans.";
   }
}
