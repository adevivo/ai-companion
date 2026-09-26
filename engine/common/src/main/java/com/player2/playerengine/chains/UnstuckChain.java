package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.tasks.construction.DestroyBlockTask;
import com.player2.playerengine.tasks.movement.GetOutOfWaterTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.SafeRandomShimmyTask;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class UnstuckChain extends SingleTaskChain {
   private final LinkedList<Vec3> posHistory = new LinkedList<>();
   private final TimerGame shimmyTimer = new TimerGame(5.0);
   private final TimerGame placeBlockGoToBlockTimeout = new TimerGame(5.0);
   /** Tell the agent about powder snow at most this often; it re-fires every tick while stuck. */
   private final TimerGame powderSnowNoticeTimer = new TimerGame(30.0);
   private boolean isProbablyStuck = false;
   private int eatingTicks = 0;
   private boolean interruptedEating = false;
   private boolean startedShimmying = false;
   private BlockPos placeBlockGoToBlock = null;

   public UnstuckChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   public float getPriority() {
      if (this.controller != null && this.controller.getTaskRunner().isActive()) {
         this.isProbablyStuck = false;
         LivingEntity player = this.controller.getEntity();
         this.posHistory.addFirst(player.position());
         if (this.posHistory.size() > 500) {
            this.posHistory.removeLast();
         }

         this.checkStuckInWater();
         this.checkStuckInPowderSnow();
         this.checkEatingGlitch();
         this.checkStuckOnEndPortalFrame();
         if (this.isProbablyStuck) {
            return 65.0F;
         } else if (this.startedShimmying && !this.shimmyTimer.elapsed()) {
            this.setTask(new SafeRandomShimmyTask());
            return 65.0F;
         } else {
            this.startedShimmying = false;
            if (this.placeBlockGoToBlockTimeout.elapsed()) {
               this.placeBlockGoToBlock = null;
            }

            if (this.placeBlockGoToBlock != null) {
               this.setTask(new GetToBlockTask(this.placeBlockGoToBlock, false));
               return 65.0F;
            } else {
               return Float.NEGATIVE_INFINITY;
            }
         }
      } else {
         return Float.NEGATIVE_INFINITY;
      }
   }

   private void checkStuckInWater() {
      if (this.posHistory.size() >= 100) {
         LivingEntity player = this.controller.getEntity();
         Level world = this.controller.getWorld();
         if (world.getBlockState(player.blockPosition()).is(Blocks.WATER)) {
            if (!player.onGround() && player.getAirSupply() >= player.getMaxAirSupply()) {
               Vec3 firstPos = this.posHistory.get(0);

               for (int i = 1; i < 100; i++) {
                  Vec3 nextPos = this.posHistory.get(i);
                  if (Math.abs(firstPos.x() - nextPos.x()) > 0.75 || Math.abs(firstPos.z() - nextPos.z()) > 0.75) {
                     return;
                  }
               }

               this.posHistory.clear();
               this.setTask(new GetOutOfWaterTask());
               this.isProbablyStuck = true;
            } else {
               this.posHistory.clear();
            }
         }
      }
   }

   /**
    * Break out of powder snow — the highest drift the body is in first, so the head clears before the
    * feet — and shimmy when none is left but the body is still caught.
    *
    * <p>Checks every block the body touches rather than the one column under its centre: a companion
    * half in the next column over was invisible to that. {@code isInPowderSnow} alone is not enough
    * either — vanilla clears it at the start of each entity tick and sets it again during movement, so
    * what it reads depends on when this chain ticks. The blocks do not.
    */
   private void checkStuckInPowderSnow() {
      LivingEntity player = this.controller.getEntity();
      BlockPos toBreak = null;
      for (BlockPos pos : WorldHelper.getBlocksTouchingPlayer(player)) {
         if (player.level().getBlockState(pos).is(Blocks.POWDER_SNOW) && (toBreak == null || pos.getY() > toBreak.getY())) {
            toBreak = pos.immutable();
         }
      }

      if (toBreak == null && !player.isInPowderSnow) {
         return;
      }

      this.isProbablyStuck = true;
      if (this.powderSnowNoticeTimer.elapsed()) {
         this.powderSnowNoticeTimer.reset();
         this.controller.logAgentInfo(
            "Stuck in powder snow" + (player.getTicksFrozen() > 0 ? " and freezing" : "") + " — breaking out of it now. "
               + "Powder snow swallows anything walking on it; leather boots let you walk on top instead, so `equip leather_boots` if you have them."
         );
      }

      if (toBreak != null) {
         this.setTask(new DestroyBlockTask(toBreak));
      } else {
         this.setTask(new SafeRandomShimmyTask());
      }
   }

   private void checkStuckOnEndPortalFrame() {
      BlockState standingOn = this.controller.getWorld().getBlockState(this.controller.getEntity().getOnPos());
      if (standingOn.is(Blocks.END_PORTAL_FRAME)
         && !(Boolean)standingOn.getValue(EndPortalFrameBlock.HAS_EYE)
         && !this.controller.getFoodChain().isTryingToEat()) {
         this.isProbablyStuck = true;
         this.controller.getBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
      }
   }

   private void checkEatingGlitch() {
      FoodChain foodChain = this.controller.getFoodChain();
      if (this.interruptedEating) {
         foodChain.shouldStop(false);
         this.interruptedEating = false;
      }

      if (foodChain.isTryingToEat()) {
         this.eatingTicks++;
      } else {
         this.eatingTicks = 0;
      }

      if (this.eatingTicks > 140) {
         Debug.logMessage("Bot is probably stuck trying to eat. Resetting action.");
         foodChain.shouldStop(true);
         this.eatingTicks = 0;
         this.interruptedEating = true;
         this.isProbablyStuck = true;
      }
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   protected void onTaskFinish(PlayerEngineController controller) {
   }

   @Override
   public String getName() {
      return "Unstuck Chain";
   }
}
