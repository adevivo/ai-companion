package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.movement.SearchChunkForBlockTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class GetSmithingTemplateTask extends ResourceTask {
   private final Task searcher = new SearchChunkForBlockTask(Blocks.BLACKSTONE);
   private final int count;
   private BlockPos chestloc = null;

   public GetSmithingTemplateTask(int count) {
      super(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, count);
      this.count = count;
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
         if (this.chestloc == null) {
            for (BlockPos pos : mod.getBlockScanner().getKnownLocations(Blocks.CHEST)) {
               if (WorldHelper.isInteractableBlock(mod, pos)) {
                  this.chestloc = pos;
                  break;
               }
            }
         }

         if (this.chestloc != null) {
            this.setDebugState("Destroying Chest");
            if (WorldHelper.isInteractableBlock(mod, this.chestloc)) {
               return new DestroyBlockTask(this.chestloc);
            }

            this.chestloc = null;

            for (BlockPos posx : mod.getBlockScanner().getKnownLocations(Blocks.CHEST)) {
               if (WorldHelper.isInteractableBlock(mod, posx)) {
                  this.chestloc = posx;
                  break;
               }
            }
         }

         this.setDebugState("Searching for/Traveling around bastion");
         return this.searcher;
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof GetSmithingTemplateTask;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect " + this.count + " smithing templates";
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }
}
