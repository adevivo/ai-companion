package com.player2.playerengine.tasks.construction;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.ITaskRequiresGrounded;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.automaton.api.schematic.AbstractSchematic;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import com.player2.playerengine.automaton.api.utils.input.Input;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.ArrayUtils;

public class PlaceBlockTask extends Task implements ITaskRequiresGrounded {
   private static final int MIN_MATERIALS = 1;
   private static final int PREFERRED_MATERIALS = 32;
   private final BlockPos target;
   private final Block[] toPlace;
   private final boolean useThrowaways;
   private final boolean autoCollectStructureBlocks;
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5.0F);
   private Task materialTask;
   private int failCount = 0;

   public PlaceBlockTask(BlockPos target, Block[] toPlace, boolean useThrowaways, boolean autoCollectStructureBlocks) {
      this.target = target;
      this.toPlace = toPlace;
      this.useThrowaways = useThrowaways;
      this.autoCollectStructureBlocks = autoCollectStructureBlocks;
   }

   public PlaceBlockTask(BlockPos target, Block... toPlace) {
      this(target, toPlace, false, false);
   }

   public int getMaterialCount(PlayerEngineController mod) {
      int count = mod.getItemStorage().getItemCount(ItemHelper.blocksToItems(this.toPlace));
      if (this.useThrowaways) {
         count += mod.getItemStorage().getItemCount(mod.getBaritoneSettings().acceptableThrowawayItems.get().toArray(new Item[0]));
      }

      return count;
   }

   public static Task getMaterialTask(int count) {
      return TaskCatalogue.getSquashedItemTask(
         new ItemTarget(Items.DIRT, count),
         new ItemTarget(Items.COBBLESTONE, count),
         new ItemTarget(Items.NETHERRACK, count),
         new ItemTarget(Items.COBBLED_DEEPSLATE, count)
      );
   }

   @Override
   protected void onStart() {
      this.progressChecker.reset();
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
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

      if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
         this.setDebugState("Wandering.");
         this.progressChecker.reset();
         return this.wanderTask;
      } else {
         if (this.autoCollectStructureBlocks) {
            if (this.materialTask != null && this.materialTask.isActive() && !this.materialTask.isFinished()) {
               this.setDebugState("No structure items, collecting cobblestone + dirt as default.");
               if (this.getMaterialCount(mod) < 32) {
                  return this.materialTask;
               }

               this.materialTask = null;
            }

            if (this.getMaterialCount(mod) < 1) {
               this.materialTask = getMaterialTask(32);
               this.progressChecker.reset();
               return this.materialTask;
            }
         }

         if (!this.progressChecker.check(mod)) {
            this.failCount++;
            if (!this.tryingAlternativeWay()) {
               Debug.logMessage("Failed to place, wandering timeout.");
               return this.wanderTask;
            }

            Debug.logMessage("Trying alternative way of placing block...");
         }

         if (this.tryingAlternativeWay()) {
            this.setDebugState("Alternative way: Trying to go above block to place block.");
            return new GetToBlockTask(this.target.above(), false);
         } else {
            this.setDebugState("Letting baritone place a block.");
            if (!mod.getBaritone().getBuilderProcess().isActive()) {
               Debug.logInternal("Run Structure Build");
               PlaceBlockTask.PlaceStructureSchematic placeStructureSchematic = new PlaceBlockTask.PlaceStructureSchematic(mod);
               mod.getBaritone().getBuilderProcess().build("structure", placeStructureSchematic, this.target);
            }

            return null;
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBaritone().getBuilderProcess().onLostControl();
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof PlaceBlockTask task)
         ? false
         : task.target.equals(this.target) && task.useThrowaways == this.useThrowaways && Arrays.equals((Object[])task.toPlace, (Object[])this.toPlace);
   }

   @Override
   public boolean isFinished() {
      assert this.controller.getWorld() != null;

      if (this.useThrowaways) {
         return WorldHelper.isSolidBlock(this.controller, this.target);
      } else {
         BlockState state = this.controller.getWorld().getBlockState(this.target);
         return ArrayUtils.contains(this.toPlace, state.getBlock());
      }
   }

   @Override
   protected String toDebugString() {
      return "Place structure" + ArrayUtils.toString(this.toPlace) + " at " + this.target.toShortString();
   }

   private boolean tryingAlternativeWay() {
      return this.failCount % 4 == 3;
   }

   private class PlaceStructureSchematic extends AbstractSchematic {
      private final PlayerEngineController mod;

      public PlaceStructureSchematic(PlayerEngineController mod) {
         super(1, 1, 1);
         this.mod = mod;
      }

      @Override
      public BlockState desiredState(int x, int y, int z, BlockState blockState, List<BlockState> available) {
         if (x == 0 && y == 0 && z == 0) {
            if (!available.isEmpty()) {
               for (BlockState possible : available) {
                  if (possible != null) {
                     if (PlaceBlockTask.this.useThrowaways
                        && this.mod.getBaritone().settings().acceptableThrowawayItems.get().contains(possible.getBlock().asItem())) {
                        return possible;
                     }

                     if (Arrays.asList(PlaceBlockTask.this.toPlace).contains(possible.getBlock())) {
                        return possible;
                     }
                  }
               }
            }

            Debug.logInternal("Failed to find throwaway block");
            return new BlockOptionalMeta(this.mod.getWorld(), Blocks.COBBLESTONE).getAnyBlockState();
         } else {
            return blockState;
         }
      }
   }
}
