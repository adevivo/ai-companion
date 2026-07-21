package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.GetToXZTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingApproachPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonLandingPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class KillEnderDragonWithBedsTask extends Task {
   private final WaitForDragonAndPearlTask whenNotPerchingTask;
   TimerGame placeBedTimer = new TimerGame(0.6);
   TimerGame waiTimer = new TimerGame(0.3);
   TimerGame waitBeforePlaceTimer = new TimerGame(0.5);
   boolean waited = false;
   double prevDist = 100.0;
   private BlockPos endPortalTop;
   private Task freePortalTopTask = null;
   private Task placeObsidianTask = null;
   private boolean dragonDead = false;

   public KillEnderDragonWithBedsTask() {
      this.whenNotPerchingTask = new WaitForDragonAndPearlTask();
   }

   public static BlockPos locateExitPortalTop(AltoClefController mod) {
      if (!mod.getChunkTracker().isChunkLoaded(new BlockPos(0, 64, 0))) {
         return null;
      } else {
         int height = WorldHelper.getGroundHeight(mod, 0, 0, Blocks.BEDROCK);
         return height != -1 ? new BlockPos(0, height, 0) : null;
      }
   }

   @Override
   protected void onStart() {
      this.controller.getBehaviour().avoidBlockPlacing(pos -> pos.getZ() == 0 && Math.abs(pos.getX()) < 5);
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.endPortalTop == null) {
         this.endPortalTop = locateExitPortalTop(mod);
         if (this.endPortalTop != null) {
            this.whenNotPerchingTask.setExitPortalTop(this.endPortalTop);
         }
      }

      if (this.endPortalTop == null) {
         this.setDebugState("Searching for end portal top.");
         return new GetToXZTask(0, 0);
      } else {
         BlockPos obsidianTarget = this.endPortalTop.above().relative(Direction.NORTH);
         if (!mod.getWorld().getBlockState(obsidianTarget).getBlock().equals(Blocks.OBSIDIAN)) {
            if (WorldHelper.inRangeXZ(mod.getPlayer().position(), new Vec3(0.0, 0.0, 0.0), 10.0)) {
               if (this.placeObsidianTask == null) {
                  this.placeObsidianTask = new PlaceBlockTask(obsidianTarget, Blocks.OBSIDIAN);
               }

               return this.placeObsidianTask;
            } else {
               return new GetToXZTask(0, 0);
            }
         } else {
            BlockState stateAtPortal = mod.getWorld().getBlockState(this.endPortalTop.above());
            if (!stateAtPortal.isAir()
               && !stateAtPortal.getBlock().equals(Blocks.FIRE)
               && !Arrays.stream(ItemHelper.itemsToBlocks(ItemHelper.BED)).toList().contains(stateAtPortal.getBlock())) {
               if (this.freePortalTopTask == null) {
                  this.freePortalTopTask = new DestroyBlockTask(this.endPortalTop.above());
               }

               return this.freePortalTopTask;
            } else if (this.dragonDead) {
               this.setDebugState("Waiting for overworld portal to spawn.");
               return new GetToBlockTask(this.endPortalTop.below(4).west());
            } else {
               if (!mod.getEntityTracker().entityFound(EnderDragon.class) || this.dragonDead) {
                  this.setDebugState("No dragon found.");
                  if (!WorldHelper.inRangeXZ(mod.getPlayer(), this.endPortalTop, 1.0)) {
                     this.setDebugState("Going to end portal top at" + this.endPortalTop.toString() + ".");
                     return new GetToBlockTask(this.endPortalTop);
                  }
               }

               for (EnderDragon dragon : mod.getEntityTracker().getTrackedEntities(EnderDragon.class)) {
                  DragonPhaseInstance dragonPhase = dragon.getPhaseManager().getCurrentPhase();
                  if (dragonPhase.getPhase() == EnderDragonPhase.DYING) {
                     Debug.logMessage("Dragon is dead.");
                     if (mod.getPlayer().getXRot() != -90.0F) {
                        mod.getPlayer().setXRot(-90.0F);
                     }

                     this.dragonDead = true;
                     return null;
                  }

                  boolean perching = dragonPhase instanceof DragonLandingPhase || dragonPhase instanceof DragonLandingApproachPhase || dragonPhase.isSitting();
                  if (dragon.getY() < this.endPortalTop.getY() + 2) {
                     perching = false;
                  }

                  this.whenNotPerchingTask.setPerchState(perching);
                  if (this.whenNotPerchingTask.isActive() && !this.whenNotPerchingTask.isFinished()) {
                     this.setDebugState("Dragon not perching, performing special behavior...");
                     return this.whenNotPerchingTask;
                  }

                  if (perching) {
                     return this.performOneCycle(mod, dragon);
                  }
               }

               mod.getFoodChain().shouldStop(false);
               return this.whenNotPerchingTask;
            }
         }
      }
   }

   private Task performOneCycle(AltoClefController mod, EnderDragon dragon) {
      mod.getFoodChain().shouldStop(true);
      if (mod.getInputControls().isHeldDown(Input.SNEAK)) {
         mod.getInputControls().release(Input.SNEAK);
      }

      mod.getSlotHandler().forceEquipItemToOffhand(Items.AIR);
      BlockPos endPortalTop = locateExitPortalTop(mod).above();
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
      } else {
         Direction offsetDir = dir.getAxis() == Axis.X ? Direction.SOUTH : Direction.WEST;
         BlockPos targetBlock = endPortalTop.below(3).relative(offsetDir, 3).relative(dir);
         double d = this.distanceIgnoreY(WorldHelper.toVec3d(targetBlock), mod.getPlayer().position());
         if (!(d > 0.7) && mod.getPlayer().blockPosition().below().getY() <= endPortalTop.getY() - 4) {
            if (!this.waited) {
               this.waited = true;
               this.waitBeforePlaceTimer.reset();
            }

            if (!this.waitBeforePlaceTimer.elapsed()) {
               mod.log(this.waitBeforePlaceTimer.getDuration() + " waiting...");
               return null;
            } else {
               LookHelper.lookAt(mod, obsidian, dir);
               BlockPos bedHead = WorldHelper.getBedHead(mod, endPortalTop);
               mod.getSlotHandler().forceEquipItem(ItemHelper.BED);
               if (bedHead == null) {
                  if (this.placeBedTimer.elapsed() && Math.abs(dragon.getY() - endPortalTop.getY()) < 10.0) {
                     mod.getInputControls().tryPress(Input.CLICK_RIGHT);
                     this.waiTimer.reset();
                  }

                  return null;
               } else if (!this.waiTimer.elapsed()) {
                  return null;
               } else {
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
                  return null;
               }
            }
         } else {
            mod.log(d + "");
            return new GetToBlockTask(targetBlock);
         }
      }
   }

   public double distanceIgnoreY(Vec3 vec, Vec3 vec1) {
      double d = vec.x - vec1.x;
      double f = vec.z - vec1.z;
      return Math.sqrt(d * d + f * f);
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getFoodChain().shouldStop(false);
   }

   @Override
   public boolean isFinished() {
      return super.isFinished();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof KillEnderDragonWithBedsTask;
   }

   @Override
   protected String toDebugString() {
      return "Bedding the Ender Dragon";
   }
}
