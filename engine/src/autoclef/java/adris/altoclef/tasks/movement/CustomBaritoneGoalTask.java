package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.control.InputControls;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;

public abstract class CustomBaritoneGoalTask extends Task implements ITaskRequiresGrounded {
   private final Task wanderTask = new TimeoutWanderTask(5.0F, true);
   private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
   private final boolean wander;
   protected MovementProgressChecker checker = new MovementProgressChecker();
   protected Goal cachedGoal = null;
   Block[] annoyingBlocks = new Block[]{
      Blocks.VINE,
      Blocks.NETHER_SPROUTS,
      Blocks.CAVE_VINES,
      Blocks.CAVE_VINES_PLANT,
      Blocks.TWISTING_VINES,
      Blocks.TWISTING_VINES_PLANT,
      Blocks.WEEPING_VINES_PLANT,
      Blocks.LADDER,
      Blocks.BIG_DRIPLEAF,
      Blocks.BIG_DRIPLEAF_STEM,
      Blocks.SMALL_DRIPLEAF,
      Blocks.TALL_GRASS,
      Blocks.GRASS,
      Blocks.SWEET_BERRY_BUSH
   };
   private Task unstuckTask = null;

   public CustomBaritoneGoalTask(boolean wander) {
      this.wander = wander;
   }

   public CustomBaritoneGoalTask() {
      this(true);
   }

   private static BlockPos[] generateSides(BlockPos pos) {
      return new BlockPos[]{
         pos.offset(1, 0, 0),
         pos.offset(-1, 0, 0),
         pos.offset(0, 0, 1),
         pos.offset(0, 0, -1),
         pos.offset(1, 0, -1),
         pos.offset(1, 0, 1),
         pos.offset(-1, 0, -1),
         pos.offset(-1, 0, 1)
      };
   }

   private boolean isAnnoying(AltoClefController mod, BlockPos pos) {
      Block[] arrayOfBlock = this.annoyingBlocks;
      int i = arrayOfBlock.length;
      byte b = 0;
      if (b >= i) {
         return false;
      } else {
         Block AnnoyingBlocks = arrayOfBlock[b];
         return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks
            || mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock
            || mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
      }
   }

   private BlockPos stuckInBlock(AltoClefController mod) {
      BlockPos p = mod.getPlayer().blockPosition();
      if (this.isAnnoying(mod, p)) {
         return p;
      } else if (this.isAnnoying(mod, p.above())) {
         return p.above();
      } else {
         BlockPos[] toCheck = generateSides(p);

         for (BlockPos check : toCheck) {
            if (this.isAnnoying(mod, check)) {
               return check;
            }
         }

         BlockPos[] toCheckHigh = generateSides(p.above());

         for (BlockPos checkx : toCheckHigh) {
            if (this.isAnnoying(mod, checkx)) {
               return checkx;
            }
         }

         return null;
      }
   }

   private Task getFenceUnstuckTask() {
      return new SafeRandomShimmyTask();
   }

   @Override
   protected void onStart() {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
      this.checker.reset();
      this.stuckCheck.reset();
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      InputControls controls = mod.getInputControls();
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.checker.reset();
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         if (!mod.getBaritone().getPathingBehavior().isPathing()) {
            this.setDebugState("Getting out from nether portal");
            controls.hold(Input.SNEAK);
            controls.hold(Input.MOVE_FORWARD);
            return null;
         }

         controls.release(Input.SNEAK);
         controls.release(Input.MOVE_BACK);
         controls.release(Input.MOVE_FORWARD);
      } else if (mod.getBaritone().getPathingBehavior().isPathing()) {
         controls.release(Input.SNEAK);
         controls.release(Input.MOVE_BACK);
         controls.release(Input.MOVE_FORWARD);
      }

      if (this.unstuckTask != null && this.unstuckTask.isActive() && !this.unstuckTask.isFinished() && this.stuckInBlock(mod) != null) {
         this.setDebugState("Getting unstuck from block.");
         this.stuckCheck.reset();
         mod.getBaritone().getCustomGoalProcess().onLostControl();
         mod.getBaritone().getExploreProcess().onLostControl();
         return this.unstuckTask;
      } else {
         if (!this.checker.check(mod) || !this.stuckCheck.check(mod)) {
            BlockPos blockStuck = this.stuckInBlock(mod);
            if (blockStuck != null) {
               this.unstuckTask = this.getFenceUnstuckTask();
               return this.unstuckTask;
            }

            if (this.stuckCheck.lastBreakingBlock != null) {
               mod.getBaritone().getCustomGoalProcess().setGoalAndPath(this.cachedGoal);
            }

            this.stuckCheck.reset();
         }

         if (this.cachedGoal == null) {
            this.cachedGoal = this.newGoal(mod);
         }

         if (this.wander) {
            if (this.isFinished()) {
               this.checker.reset();
            } else {
               if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
                  this.setDebugState("Wandering...");
                  this.checker.reset();
                  return this.wanderTask;
               }

               if (!this.checker.check(mod)) {
                  Debug.logMessage("Failed to make progress on goal, wandering.");
                  this.onWander(mod);
                  return this.wanderTask;
               }
            }
         }

         if (!mod.getBaritone().getCustomGoalProcess().isActive() && mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
            mod.getBaritone().getCustomGoalProcess().setGoalAndPath(this.cachedGoal);
         }

         this.setDebugState("Completing goal.");
         return null;
      }
   }

   @Override
   public boolean isFinished() {
      if (this.cachedGoal == null) {
         this.cachedGoal = this.newGoal(this.controller);
      }

      return this.cachedGoal != null && this.cachedGoal.isInGoal(this.controller.getPlayer().blockPosition());
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
   }

   protected abstract Goal newGoal(AltoClefController var1);

   protected void onWander(AltoClefController mod) {
   }
}
