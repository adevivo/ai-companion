package com.player2.playerengine.tasks.examples;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.construction.PlaceBlockTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.base.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class ExampleTask extends Task {
   private final int numberOfStonePickaxesToGrab;
   private final BlockPos whereToPlaceCobblestone;

   public ExampleTask(int numberOfStonePickaxesToGrab, BlockPos whereToPlaceCobblestone) {
      this.numberOfStonePickaxesToGrab = numberOfStonePickaxesToGrab;
      this.whereToPlaceCobblestone = whereToPlaceCobblestone;
   }

   @Override
   protected void onStart() {
      PlayerEngineController mod = this.controller;
      mod.getBehaviour().push();
      mod.getBehaviour().addProtectedItems(Items.COBBLESTONE);
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getItemStorage().getItemCount(Items.STONE_PICKAXE) < this.numberOfStonePickaxesToGrab) {
         return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, this.numberOfStonePickaxesToGrab);
      } else if (!mod.getItemStorage().hasItem(Items.COBBLESTONE)) {
         return TaskCatalogue.getItemTask(Items.COBBLESTONE, 1);
      } else if (mod.getChunkTracker().isChunkLoaded(this.whereToPlaceCobblestone)) {
         return mod.getWorld().getBlockState(this.whereToPlaceCobblestone).getBlock() != Blocks.COBBLESTONE
            ? new PlaceBlockTask(this.whereToPlaceCobblestone, Blocks.COBBLESTONE)
            : null;
      } else {
         return new GetToBlockTask(this.whereToPlaceCobblestone);
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   public boolean isFinished() {
      PlayerEngineController mod = this.controller;
      return mod.getItemStorage().getItemCount(Items.STONE_PICKAXE) >= this.numberOfStonePickaxesToGrab
         && mod.getWorld().getBlockState(this.whereToPlaceCobblestone).getBlock() == Blocks.COBBLESTONE;
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof ExampleTask task)
         ? false
         : task.numberOfStonePickaxesToGrab == this.numberOfStonePickaxesToGrab && task.whereToPlaceCobblestone.equals(this.whereToPlaceCobblestone);
   }

   @Override
   protected String toDebugString() {
      return "Boofin";
   }
}
