package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.construction.compound.ConstructNetherPortalObsidianTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.NotImplementedException;

public class FastTravelTask extends Task {
   private static final double IN_NETHER_CLOSE_ENOUGH_THRESHOLD = 15.0;
   private final boolean collectPortalMaterialsIfAbsent;
   private final BlockPos target;
   private final Integer threshold;
   private final TimerGame attemptToMoveToIdealNetherCoordinateTimeout = new TimerGame(15.0);
   private boolean forceOverworldWalking;
   private Task goToOverworldTask;

   public FastTravelTask(BlockPos overworldTarget, Integer threshold, boolean collectPortalMaterialsIfAbsent) {
      this.target = overworldTarget;
      this.threshold = null;
      this.collectPortalMaterialsIfAbsent = collectPortalMaterialsIfAbsent;
   }

   public FastTravelTask(BlockPos overworldTarget, boolean collectPortalMaterialsIfAbsent) {
      this(overworldTarget, null, collectPortalMaterialsIfAbsent);
   }

   @Override
   protected void onStart() {
      BlockPos netherTarget = new BlockPos(this.target.getX() / 8, this.target.getY(), this.target.getZ() / 8);
      this.goToOverworldTask = new EnterNetherPortalTask(
         new ConstructNetherPortalObsidianTask(), Dimension.OVERWORLD, checkPos -> WorldHelper.inRangeXZ(checkPos, netherTarget, 7.0)
      );
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      BlockPos netherTarget = new BlockPos(this.target.getX() / 8, this.target.getY(), this.target.getZ() / 8);
      boolean canBuildPortal = mod.getItemStorage().hasItem(Items.DIAMOND_PICKAXE) || mod.getItemStorage().getItemCount(Items.OBSIDIAN) >= 10;
      boolean canLightPortal = mod.getItemStorage().hasItem(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
      switch (WorldHelper.getCurrentDimension(this.controller)) {
         case OVERWORLD:
            this.attemptToMoveToIdealNetherCoordinateTimeout.reset();
            if (!this.forceOverworldWalking && !WorldHelper.inRangeXZ(mod.getPlayer(), this.target, (double)this.getOverworldThreshold(mod))) {
               if (!canBuildPortal || !canLightPortal) {
                  if (!this.collectPortalMaterialsIfAbsent) {
                     this.setDebugState("Walking: We don't have portal building materials");
                     return new GetToBlockTask(this.target);
                  }

                  this.setDebugState("Collecting portal building materials");
                  if (!canBuildPortal) {
                     return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                  }

                  if (!canLightPortal) {
                     return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
                  }
               }

               return new DefaultGoToDimensionTask(Dimension.NETHER);
            } else {
               this.forceOverworldWalking = true;
               this.setDebugState("Walking: We're close enough to our target");
               if (mod.getBlockScanner().anyFound(Blocks.END_PORTAL_FRAME)) {
                  this.setDebugState("Walking to portal");
                  return new GetToBlockTask(mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL_FRAME).get());
               } else {
                  return new GetToBlockTask(this.target);
               }
            }
         case NETHER:
            if (!this.forceOverworldWalking) {
               Optional<BlockPos> portalEntrance = mod.getMiscBlockTracker().getLastUsedNetherPortal(Dimension.NETHER);
               if (portalEntrance.isPresent()
                  && !portalEntrance.get()
                     .closerThan(new Vec3i((int)mod.getPlayer().position().x, (int)mod.getPlayer().position().y, (int)mod.getPlayer().position().z), 3.0)) {
                  this.forceOverworldWalking = true;
               }
            }

            if (this.goToOverworldTask.isActive() && !this.goToOverworldTask.isFinished()) {
               this.setDebugState("Going back to overworld");
               return this.goToOverworldTask;
            } else if (mod.getItemStorage().getItemCount(Items.OBSIDIAN) < 10) {
               this.setDebugState("Making sure we can build our portal");
               return TaskCatalogue.getItemTask(Items.OBSIDIAN, 10);
            } else if (!canLightPortal && mod.getEntityTracker().itemDropped(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
               this.setDebugState("Making sure we can light our portal");
               return new PickupDroppedItemTask(new ItemTarget(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE), true);
            } else {
               if (!WorldHelper.inRangeXZ(mod.getPlayer(), netherTarget, 15.0)
                  || !mod.getBaritone().getPathingBehavior().isSafeToCancel()
                  || (mod.getPlayer().getBlockX() != netherTarget.getX() || mod.getPlayer().getBlockZ() != netherTarget.getZ())
                     && !this.attemptToMoveToIdealNetherCoordinateTimeout.elapsed()) {
                  this.attemptToMoveToIdealNetherCoordinateTimeout.reset();
                  this.setDebugState("Traveling to ideal coordinates");
                  return new GetToXZTask(netherTarget.getX(), netherTarget.getZ());
               }

               return this.goToOverworldTask;
            }
         case END:
            this.setDebugState("Why are you running this here?");
            return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
         default:
            throw new NotImplementedException("Unimplemented dimension: " + WorldHelper.getCurrentDimension(this.controller));
      }
   }

   private int getOverworldThreshold(AltoClefController mod) {
      int threshold;
      if (this.threshold == null) {
         threshold = mod.getModSettings().getNetherFastTravelWalkingRange();
      } else {
         threshold = this.threshold;
      }

      threshold = Math.max(152, threshold);
      return Math.max(128, threshold);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof FastTravelTask task)
         ? false
         : task.target.equals(this.target)
            && task.collectPortalMaterialsIfAbsent == this.collectPortalMaterialsIfAbsent
            && Objects.equals(task.threshold, this.threshold);
   }

   @Override
   protected String toDebugString() {
      return "Fast travelling to " + this.target.toShortString();
   }
}
