package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.baritone.GoalFollowEntity;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;

public class GetToEntityTask extends Task implements ITaskRequiresGrounded {
   private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
   private final MovementProgressChecker progress = new MovementProgressChecker();
   private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5.0F);
   private final Entity entity;
   private final double closeEnoughDistance;
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

   public GetToEntityTask(Entity entity, double closeEnoughDistance) {
      this.entity = entity;
      this.closeEnoughDistance = closeEnoughDistance;
   }

   public GetToEntityTask(Entity entity) {
      this(entity, 1.0);
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
      if (this.annoyingBlocks != null) {
         Block[] arrayOfBlock = this.annoyingBlocks;
         int i = arrayOfBlock.length;
         byte b = 0;
         if (b < i) {
            Block AnnoyingBlocks = arrayOfBlock[b];
            return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks
               || mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
         }
      }

      return false;
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
      this.progress.reset();
      this.stuckCheck.reset();
      this.wanderTask.resetWander();
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (mod.getBaritone().getPathingBehavior().isPathing()) {
         this.progress.reset();
      }

      if (WorldHelper.isInNetherPortal(this.controller)) {
         if (!mod.getBaritone().getPathingBehavior().isPathing()) {
            this.setDebugState("Getting out from nether portal");
            mod.getInputControls().hold(Input.SNEAK);
            mod.getInputControls().hold(Input.MOVE_FORWARD);
            return null;
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      } else if (mod.getBaritone().getPathingBehavior().isPathing()) {
         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.MOVE_BACK);
         mod.getInputControls().release(Input.MOVE_FORWARD);
      }

      if (this.unstuckTask != null && this.unstuckTask.isActive() && !this.unstuckTask.isFinished() && this.stuckInBlock(mod) != null) {
         this.setDebugState("Getting unstuck from block.");
         this.stuckCheck.reset();
         mod.getBaritone().getCustomGoalProcess().onLostControl();
         mod.getBaritone().getExploreProcess().onLostControl();
         return this.unstuckTask;
      } else {
         if (!this.progress.check(mod) || !this.stuckCheck.check(mod)) {
            BlockPos blockStuck = this.stuckInBlock(mod);
            if (blockStuck != null) {
               this.unstuckTask = this.getFenceUnstuckTask();
               return this.unstuckTask;
            }

            this.stuckCheck.reset();
         }

         if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
            this.progress.reset();
            this.setDebugState("Failed to get to target, wandering for a bit.");
            return this.wanderTask;
         } else {
            if (!mod.getBaritone().getCustomGoalProcess().isActive()) {
               mod.getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalFollowEntity(this.entity, this.closeEnoughDistance));
            }

            if (mod.getPlayer().closerThan(this.entity, this.closeEnoughDistance)) {
               this.progress.reset();
            }

            if (!this.progress.check(mod)) {
               return this.wanderTask;
            } else {
               this.setDebugState("Going to entity");
               return null;
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof GetToEntityTask task)
         ? false
         : task.entity.equals(this.entity) && Math.abs(task.closeEnoughDistance - this.closeEnoughDistance) < 0.1;
   }

   @Override
   protected String toDebugString() {
      return "Approach entity " + this.entity.getType().getDescriptionId();
   }
}
