package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.ILookBehavior;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.utils.InputOverrideHandler;
import com.google.common.base.Preconditions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public final class LookBehavior extends Behavior implements ILookBehavior {
   private Rotation target;
   private Rotation secondaryTarget;
   private boolean force;

   public LookBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void updateSecondaryTarget(Rotation target) {
      this.secondaryTarget = target;
   }

   @Override
   public void updateTarget(Rotation target, boolean force) {
      this.target = target;
      if (!force) {
         double rand = Math.random() - 0.5;
         if (Math.abs(rand) < 0.1) {
            rand *= 4.0;
         }

         this.target = new Rotation(this.target.getYaw() + (float)(rand * this.baritone.settings().randomLooking113.get()), this.target.getPitch());
      }

      this.force = force;
   }

   @Override
   public void onTickServer() {
      if (this.target != null || this.secondaryTarget != null) {
         this.updateLook(this.target, this.force, this.secondaryTarget);
      }

      this.target = null;
      this.secondaryTarget = null;
      this.force = false;
   }

   @Contract("null, true,  -> fail; null, , null -> fail")
   private void updateLook(@Nullable Rotation primaryTarget, boolean forcePrimary, @Nullable Rotation secondaryTarget) {
      Preconditions.checkArgument(primaryTarget != null || !forcePrimary);
      Preconditions.checkArgument(primaryTarget != null || secondaryTarget != null);
      Rotation actualTarget;
      if (!forcePrimary && !this.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT)) {
         actualTarget = this.getActualTarget(primaryTarget, secondaryTarget);
         if (actualTarget == null) {
            return;
         }
      } else {
         actualTarget = primaryTarget;
      }

      assert actualTarget != null;

      LivingEntity entity = this.ctx.entity();
      double lookScrambleFactor = this.baritone.settings().randomLooking.get();
      updateLook(entity, actualTarget, lookScrambleFactor, !this.baritone.settings().freeLook.get());
   }

   private static void updateLook(LivingEntity entity, Rotation target, double lookScrambleFactor, boolean nudgePitch) {
      entity.setYRot(target.getYaw());
      float oldPitch = entity.getXRot();
      float desiredPitch = target.getPitch();
      entity.setXRot(desiredPitch);
      entity.setYRot((float)(entity.getYRot() + (Math.random() - 0.5) * lookScrambleFactor));
      entity.setXRot((float)(entity.getXRot() + (Math.random() - 0.5) * lookScrambleFactor));
      if (desiredPitch == oldPitch && nudgePitch) {
         nudgeToLevel(entity);
      }
   }

   @Nullable
   private Rotation getActualTarget(@Nullable Rotation primaryTarget, @Nullable Rotation secondaryTarget) {
      if (this.baritone.settings().freeLook.get()) {
         updateControlsToMatch(this.baritone.getInputOverrideHandler(), primaryTarget, this.ctx.entity().getYRot());
         return null;
      } else if (secondaryTarget != null) {
         updateControlsToMatch(this.baritone.getInputOverrideHandler(), primaryTarget, secondaryTarget.getYaw());
         return secondaryTarget;
      } else {
         return primaryTarget;
      }
   }

   public void pig() {
      if (this.target != null) {
         this.ctx.entity().setYRot(this.target.getYaw());
      }
   }

   private static void updateControlsToMatch(InputOverrideHandler inputs, Rotation target, float actualYaw) {
      if (target != null) {
         if (inputs.isInputForcedDown(Input.MOVE_FORWARD)) {
            float desiredYaw = Mth.wrapDegrees(target.getYaw());
            float yawDifference = Mth.degreesDifference(actualYaw, desiredYaw);
            float absoluteDifference = Math.abs(yawDifference);
            if (absoluteDifference >= 89.0F) {
               inputs.setInputForceState(Input.MOVE_FORWARD, false);
               if (absoluteDifference >= 91.0F) {
                  inputs.setInputForceState(Input.MOVE_BACK, true);
               }
            }

            if (absoluteDifference >= 1.0F && absoluteDifference <= 179.0F) {
               if (yawDifference > 0.0F) {
                  inputs.setInputForceState(Input.MOVE_RIGHT, true);
               } else {
                  inputs.setInputForceState(Input.MOVE_LEFT, true);
               }
            }
         }
      }
   }

   private static void nudgeToLevel(LivingEntity entity) {
      if (entity.getXRot() < -20.0F) {
         entity.setXRot(entity.getXRot() + 1.0F);
      } else if (entity.getXRot() > 10.0F) {
         entity.setXRot(entity.getXRot() - 1.0F);
      }
   }
}
