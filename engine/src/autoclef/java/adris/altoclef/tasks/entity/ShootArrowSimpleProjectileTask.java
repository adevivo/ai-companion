package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ShootArrowSimpleProjectileTask extends Task {
   private final Entity target;
   private boolean shooting = false;
   private boolean shot = false;
   private final TimerGame shotTimer = new TimerGame(1.0);

   public ShootArrowSimpleProjectileTask(Entity target) {
      this.target = target;
   }

   @Override
   protected void onStart() {
      this.shooting = false;
   }

   private static Rotation calculateThrowLook(AltoClefController mod, Entity target) {
      float velocity = (mod.getPlayer().getTicksUsingItem() - mod.getPlayer().getUseItemRemainingTicks()) / 20.0F;
      velocity = (velocity * velocity + velocity * 2.0F) / 3.0F;
      if (velocity > 1.0F) {
         velocity = 1.0F;
      }

      Vec3 targetCenter = target.getBoundingBox().getCenter();
      double posX = targetCenter.x();
      double posY = targetCenter.y();
      double posZ = targetCenter.z();
      posY -= 1.9F - target.getBbHeight();
      double relativeX = posX - mod.getPlayer().getX();
      double relativeY = posY - mod.getPlayer().getY();
      double relativeZ = posZ - mod.getPlayer().getZ();
      double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
      double hDistanceSq = hDistance * hDistance;
      float g = 0.006F;
      float velocitySq = velocity * velocity;
      float pitch = (float)(
         -Math.toDegrees(
            Math.atan((velocitySq - Math.sqrt(velocitySq * velocitySq - 0.006F * (0.006F * hDistanceSq + 2.0 * relativeY * velocitySq))) / 0.006F * hDistance)
         )
      );
      return Float.isNaN(pitch) ? new Rotation(target.getYRot(), target.getXRot()) : new Rotation(Vec3dToYaw(mod, new Vec3(posX, posY, posZ)), pitch);
   }

   private static float Vec3dToYaw(AltoClefController mod, Vec3 vec) {
      return mod.getPlayer().getYRot()
         + Mth.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(vec.z() - mod.getPlayer().getZ(), vec.x() - mod.getPlayer().getX())) - 90.0F - mod.getPlayer().getYRot()
         );
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      this.setDebugState("Shooting projectile");
      List<Item> requiredArrows = Arrays.asList(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW);
      if (mod.getItemStorage().hasItem(Items.BOW) && requiredArrows.stream().anyMatch(mod.getItemStorage()::hasItem)) {
         Rotation lookTarget = calculateThrowLook(mod, this.target);
         LookHelper.lookAt(this.controller, lookTarget);
         boolean charged = mod.getPlayer().getTicksUsingItem() > 20 && mod.getPlayer().getUseItem().getItem() == Items.BOW;
         mod.getSlotHandler().forceEquipItem(Items.BOW);
         if (LookHelper.isLookingAt(mod, lookTarget) && !this.shooting) {
            mod.getInputControls().hold(Input.CLICK_RIGHT);
            this.shooting = true;
            this.shotTimer.reset();
         }

         if (this.shooting && charged) {
            for (Arrow arrow : mod.getEntityTracker().getTrackedEntities(Arrow.class)) {
               if (arrow.getOwner() == mod.getPlayer()) {
                  Vec3 velocity = arrow.getDeltaMovement();
                  Vec3 delta = this.target.position().subtract(arrow.position());
                  boolean isMovingTowardsTarget = velocity.dot(delta) > 0.0;
                  if (isMovingTowardsTarget) {
                     return null;
                  }
               }
            }

            mod.getInputControls().release(Input.CLICK_RIGHT);
            this.shot = true;
         }

         return null;
      } else {
         Debug.logMessage("Missing items, stopping.");
         return null;
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getInputControls().release(Input.CLICK_RIGHT);
   }

   @Override
   public boolean isFinished() {
      return this.shot;
   }

   @Override
   protected boolean isEqual(Task other) {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "Shooting arrow at " + this.target.getType().getDescriptionId();
   }
}
