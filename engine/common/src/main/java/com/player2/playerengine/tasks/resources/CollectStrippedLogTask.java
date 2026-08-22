package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.InteractWithBlockTask;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class CollectStrippedLogTask extends ResourceTask {
   private static final Item[] axes = new Item[]{Items.WOODEN_AXE, Items.STONE_AXE, Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
   private final Item[] strippedLogs;
   private final Item[] strippableLogs;
   private final int targetCount;

   public CollectStrippedLogTask(Item[] strippedLogs, Item[] strippableLogs, int count) {
      super(new ItemTarget(strippedLogs, count));
      this.strippedLogs = strippedLogs;
      this.strippableLogs = strippableLogs;
      this.targetCount = count;
   }

   public CollectStrippedLogTask(int count) {
      this(ItemHelper.STRIPPED_LOGS, ItemHelper.STRIPPABLE_LOGS, count);
   }

   public CollectStrippedLogTask(Item strippedLogs, Item strippableLogs, int count) {
      this(new Item[]{strippedLogs}, new Item[]{strippableLogs}, count);
   }

   public CollectStrippedLogTask(Item strippedLog, int count) {
      this(strippedLog, ItemHelper.strippedToLogs(strippedLog), count);
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
      if (!mod.getItemStorage().hasItem(axes)) {
         this.setDebugState("Getting axe for stripping");
         return TaskCatalogue.getItemTask(Items.WOODEN_AXE, 1);
      } else {
         if (mod.getItemStorage().getItemCount(this.strippedLogs) < this.targetCount) {
            Optional<BlockPos> strippedLogBlockPos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(this.strippedLogs));
            if (strippedLogBlockPos.isPresent()) {
               this.setDebugState("Getting stripped log");
               return new MineAndCollectTask(new ItemTarget(this.strippedLogs), ItemHelper.itemsToBlocks(this.strippedLogs), MiningRequirement.HAND);
            }
         }

         Optional<BlockPos> strippableLogBlockPos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(this.strippableLogs));
         if (strippableLogBlockPos.isPresent()) {
            this.setDebugState("Stripping log");
            return new InteractWithBlockTask(new ItemTarget(axes), strippableLogBlockPos.get());
         } else {
            this.setDebugState("Searching log");
            return new TimeoutWanderTask();
         }
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof CollectStrippedLogTask task ? task.targetCount == this.targetCount : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Collect Stripped Log";
   }
}
