package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.movement.MLGBucketTask;
import com.player2.playerengine.tasks.base.ITaskOverridesGrounded;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;

public class MLGBucketFallChain extends SingleTaskChain implements ITaskOverridesGrounded {
   private final TimerGame tryCollectWaterTimer = new TimerGame(4.0);
   private final TimerGame pickupRepeatTimer = new TimerGame(0.25);
   private MLGBucketTask lastMLG = null;
   private boolean wasPickingUp = false;
   private boolean doingChorusFruit = false;

   public MLGBucketFallChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(PlayerEngineController mod) {
   }

   @Override
   public float getPriority() {
      if (!PlayerEngineController.inGame()) {
         return Float.NEGATIVE_INFINITY;
      } else {
         PlayerEngineController mod = this.controller;
         if (this.isFalling(mod)) {
            this.tryCollectWaterTimer.reset();
            this.setTask(new MLGBucketTask());
            this.lastMLG = (MLGBucketTask)this.mainTask;
            return 100.0F;
         } else {
            if (!this.tryCollectWaterTimer.elapsed()
               && mod.getItemStorage().hasItem(Items.BUCKET)
               && !mod.getItemStorage().hasItem(Items.WATER_BUCKET)
               && this.lastMLG != null) {
               BlockPos placed = this.lastMLG.getWaterPlacedPos();

               boolean isPlacedWater;
               try {
                  isPlacedWater = mod.getWorld().getBlockState(placed).getBlock() == Blocks.WATER;
               } catch (Exception var6) {
                  isPlacedWater = false;
               }

               if (placed != null && placed.closerToCenterThan(mod.getPlayer().position(), 5.5) && isPlacedWater) {
                  mod.getBehaviour().push();
                  mod.getBehaviour().setRayTracingFluidHandling(Fluid.SOURCE_ONLY);
                  Optional<Rotation> reach = LookHelper.getReach(this.controller, placed, Direction.UP);
                  if (reach.isPresent()) {
                     mod.getBaritone().getLookBehavior().updateTarget(reach.get(), true);
                     if (mod.getBaritone().getEntityContext().isLookingAt(placed) && mod.getSlotHandler().forceEquipItem(Items.BUCKET)) {
                        if (this.pickupRepeatTimer.elapsed()) {
                           this.pickupRepeatTimer.reset();
                           mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                           this.wasPickingUp = true;
                        } else if (this.wasPickingUp) {
                           this.wasPickingUp = false;
                        }
                     }
                  } else {
                     this.setTask(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1));
                  }

                  mod.getBehaviour().pop();
                  return 60.0F;
               }
            }

            if (this.wasPickingUp) {
               this.wasPickingUp = false;
               this.lastMLG = null;
            }

            if (mod.getPlayer().hasEffect(MobEffects.LEVITATION)
               && ((MobEffectInstance)mod.getPlayer().getActiveEffectsMap().get(MobEffects.LEVITATION)).getDuration() <= 70
               && mod.getItemStorage().hasItemInventoryOnly(Items.CHORUS_FRUIT)
               && !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
               this.doingChorusFruit = true;
               mod.getSlotHandler().forceEquipItem(Items.CHORUS_FRUIT);
               mod.getInputControls().hold(Input.CLICK_RIGHT);
               mod.getExtraBaritoneSettings().setInteractionPaused(true);
            } else if (this.doingChorusFruit) {
               this.doingChorusFruit = false;
               mod.getInputControls().release(Input.CLICK_RIGHT);
               mod.getExtraBaritoneSettings().setInteractionPaused(false);
            }

            this.lastMLG = null;
            return Float.NEGATIVE_INFINITY;
         }
      }
   }

   @Override
   public String getName() {
      return "MLG Water Bucket Fall Chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }

   public boolean doneMLG() {
      return this.lastMLG == null;
   }

   public boolean isChorusFruiting() {
      return this.doingChorusFruit;
   }

   public boolean isFalling(PlayerEngineController mod) {
      if (!mod.getModSettings().shouldAutoMLGBucket()) {
         return false;
      } else if (!mod.getPlayer().isSwimming() && !mod.getPlayer().isInWater() && !mod.getPlayer().onGround() && !mod.getPlayer().onClimbable()) {
         double ySpeed = mod.getPlayer().getDeltaMovement().y;
         return ySpeed < -0.7;
      } else {
         return false;
      }
   }
}
