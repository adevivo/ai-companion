package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.LookHelper;
import com.player2.playerengine.util.helpers.ProjectileHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.time.TimerGame;
import com.player2.playerengine.automaton.api.utils.Rotation;
import com.player2.playerengine.automaton.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ThrowEnderPearlSimpleProjectileTask extends Task {
   private final TimerGame thrownTimer = new TimerGame(5.0);
   private final BlockPos target;
   private boolean thrown = false;

   public ThrowEnderPearlSimpleProjectileTask(BlockPos target) {
      this.target = target;
   }

   private static boolean cleanThrow(PlayerEngineController mod, float yaw, float pitch) {
      Rotation rotation = new Rotation(yaw, -1.0F * pitch);
      float range = 3.0F;
      Vec3 delta = LookHelper.toVec3d(rotation).scale(range);
      Vec3 start = LookHelper.getCameraPos(mod);
      return LookHelper.cleanLineOfSight(mod, start.add(delta), (double)range);
   }

   private static Rotation calculateThrowLook(PlayerEngineController mod, BlockPos end) {
      Vec3 start = ProjectileHelper.getThrowOrigin(mod.getPlayer());
      Vec3 endCenter = WorldHelper.toVec3d(end);
      double gravity = 0.03;
      double speed = 1.5;
      float yaw = LookHelper.getLookRotation(mod, end).getYaw();
      double flatDistance = WorldHelper.distanceXZ(start, endCenter);
      double[] pitches = ProjectileHelper.calculateAnglesForSimpleProjectileMotion(start.y - endCenter.y, flatDistance, speed, gravity);
      double pitch = cleanThrow(mod, yaw, (float)pitches[0]) ? pitches[0] : pitches[1];
      return new Rotation(yaw, -1.0F * (float)pitch);
   }

   @Override
   protected void onStart() {
      this.thrownTimer.forceElapse();
      this.thrown = false;
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      if (mod.getEntityTracker().entityFound(ThrownEnderpearl.class)) {
         this.thrownTimer.reset();
      }

      if (this.thrownTimer.elapsed() && mod.getSlotHandler().forceEquipItem(Items.ENDER_PEARL)) {
         Rotation lookTarget = calculateThrowLook(mod, this.target);
         LookHelper.lookAt(this.controller, lookTarget);
         if (LookHelper.isLookingAt(mod, lookTarget)) {
            mod.getInputControls().tryPress(Input.CLICK_RIGHT);
            this.thrown = true;
            this.thrownTimer.reset();
         }
      }

      return null;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return this.thrown && this.thrownTimer.elapsed() || !this.thrown && !this.controller.getItemStorage().hasItem(Items.ENDER_PEARL);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof ThrowEnderPearlSimpleProjectileTask task ? task.target.equals(this.target) : false;
   }

   @Override
   protected String toDebugString() {
      return "Simple Ender Pearling to " + this.target;
   }
}
