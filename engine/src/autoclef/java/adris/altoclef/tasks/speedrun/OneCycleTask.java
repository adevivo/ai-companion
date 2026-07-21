package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class OneCycleTask extends Task {
   TimerGame placeBedTimer = new TimerGame(0.6);
   TimerGame waiTimer = new TimerGame(0.3);
   double prevDist = 100.0;

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      mod.getFoodChain().shouldStop(true);
      mod.getSlotHandler().forceEquipItemToOffhand(Items.AIR);
      if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
         mod.getInputControls().release(Input.SNEAK);
      }

      List<EnderDragon> dragons = mod.getEntityTracker().getTrackedEntities(EnderDragon.class);
      if (dragons.size() != 1) {
         mod.log("No dragon? :(");
      }

      for (EnderDragon dragon : dragons) {
         BlockPos endPortalTop = KillEnderDragonWithBedsTask.locateExitPortalTop(mod).above();
         BlockPos obsidian = null;
         Direction dir = null;

         for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}) {
            if (mod.getWorld().getBlockState(endPortalTop.relative(direction)).getBlock().equals(Blocks.OBSIDIAN)) {
               obsidian = endPortalTop.relative(direction);
               dir = direction.getOpposite();
               break;
            }
         }

         if (dir == null) {
            mod.log("no obisidan? :(");
            return null;
         }

         Direction offsetDir = dir.getAxis() == Axis.X ? Direction.SOUTH : Direction.WEST;
         BlockPos targetBlock = endPortalTop.below(3).relative(offsetDir, 3).relative(dir);
         double d = this.distanceIgnoreY(WorldHelper.toVec3d(targetBlock), mod.getPlayer().position());
         if (d > 0.7) {
            mod.log(d + "");
            return new GetToBlockTask(targetBlock);
         }

         LookHelper.lookAt(mod, obsidian, dir);
         BlockPos bedHead = WorldHelper.getBedHead(mod, endPortalTop);
         BlockPos bedTargetPosition = endPortalTop.above();
         mod.getSlotHandler().forceEquipItem(ItemHelper.BED);
         if (bedHead == null) {
            if (this.placeBedTimer.elapsed() && Math.abs(dragon.getY() - endPortalTop.getY()) < 10.0) {
               mod.getInputControls().tryPress(Input.CLICK_RIGHT);
               this.waiTimer.reset();
            }
         } else {
            if (!this.waiTimer.elapsed()) {
               return null;
            }

            Vec3 dragonHeadPos = dragon.head.getBoundingBox().getCenter();
            Vec3 bedHeadPos = WorldHelper.toVec3d(bedHead);
            double dist = dragonHeadPos.distanceTo(bedHeadPos);
            double distXZ = this.distanceIgnoreY(dragonHeadPos, bedHeadPos);
            EnderDragonPart body = dragon.getSubEntities()[2];
            double destroyDistance = Math.abs(body.getBoundingBox().min(Axis.Y) - bedHeadPos.y());
            boolean tooClose = destroyDistance < 1.1;
            boolean skip = destroyDistance > 3.0 && dist > 4.5 && distXZ > 2.5;
            mod.log(destroyDistance + " : " + destroyDistance + " : " + dist);
            if ((
                  dist < 1.5
                     || this.prevDist < distXZ && destroyDistance < 4.0 && this.prevDist < 2.9
                     || destroyDistance < 2.0 && dist < 4.0
                     || destroyDistance < 1.7 && dist < 4.5
                     || tooClose
                     || destroyDistance < 2.4 && distXZ < 3.7
                     || destroyDistance < 3.5 && distXZ < 2.4
               )
               && !skip) {
               mod.getInputControls().tryPress(Input.CLICK_RIGHT);
               this.placeBedTimer.reset();
            }

            this.prevDist = distXZ;
            double var25 = dragonHeadPos.y() - bedHead.getY();
         }
      }

      return null;
   }

   public double distanceIgnoreY(Vec3 vec, Vec3 vec1) {
      double d = vec.x - vec1.x;
      double f = vec.z - vec1.z;
      return Math.sqrt(d * d + f * f);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   public boolean isFinished() {
      return this.controller.getEntityTracker().getTrackedEntities(EnderDragon.class).isEmpty();
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected boolean isEqual(Task other) {
      return false;
   }

   @Override
   protected String toDebugString() {
      return "One cycling bby";
   }
}
