package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.MovementHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

public class GetOutOfWaterTask extends CustomBaritoneGoalTask {
   private boolean startedShimmying = false;
   private final TimerGame shimmyTaskTimer = new TimerGame(5.0);

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getPlayer().getAirSupply() >= mod.getPlayer().getMaxAirSupply() && !mod.getPlayer().isUnderWater()) {
         boolean hasBlockBelow = false;

         for (int i = 0; i < 3; i++) {
            if (mod.getWorld().getBlockState(mod.getPlayer().getOnPos().below(i)).getBlock() != Blocks.WATER) {
               hasBlockBelow = true;
            }
         }

         boolean hasAirAbove = mod.getWorld().getBlockState(mod.getPlayer().blockPosition().above(2)).getBlock().equals(Blocks.AIR);
         if (hasAirAbove && hasBlockBelow && StorageHelper.getNumberOfThrowawayBlocks(mod) > 0) {
            mod.getInputControls().tryPress(Input.JUMP);
            if (mod.getPlayer().onGround()) {
               if (!this.startedShimmying) {
                  this.startedShimmying = true;
                  this.shimmyTaskTimer.reset();
               }

               return new SafeRandomShimmyTask();
            }

            mod.getSlotHandler().forceEquipItem(mod.getBaritoneSettings().acceptableThrowawayItems.get().toArray(new Item[0]));
            LookHelper.lookAt(mod, mod.getPlayer().getOnPos().below());
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
         }

         return super.onTick();
      } else {
         return super.onTick();
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
      return new GetOutOfWaterTask.EscapeFromWaterGoal(mod);
   }

   @Override
   protected boolean isEqual(Task other) {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "";
   }

   @Override
   public boolean isFinished() {
      return !this.controller.getPlayer().isInWater() && this.controller.getPlayer().onGround();
   }

   private class EscapeFromWaterGoal implements Goal {
      private AltoClefController mod;

      private EscapeFromWaterGoal(AltoClefController mod) {
         this.mod = mod;
      }

      private boolean isWater(int x, int y, int z) {
         return this.mod.getWorld() == null ? false : MovementHelper.isWater(this.mod.getWorld().getBlockState(new BlockPos(x, y, z)));
      }

      private boolean isWaterAdjacent(int x, int y, int z) {
         return this.isWater(x + 1, y, z)
            || this.isWater(x - 1, y, z)
            || this.isWater(x, y, z + 1)
            || this.isWater(x, y, z - 1)
            || this.isWater(x + 1, y, z - 1)
            || this.isWater(x + 1, y, z + 1)
            || this.isWater(x - 1, y, z - 1)
            || this.isWater(x - 1, y, z + 1);
      }

      @Override
      public boolean isInGoal(int x, int y, int z) {
         return !this.isWater(x, y, z) && !this.isWaterAdjacent(x, y, z);
      }

      @Override
      public double heuristic(int x, int y, int z) {
         if (this.isWater(x, y, z)) {
            return 1.0;
         } else {
            return this.isWaterAdjacent(x, y, z) ? 0.5 : 0.0;
         }
      }
   }
}
