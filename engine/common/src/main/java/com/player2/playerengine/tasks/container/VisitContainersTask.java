package com.player2.playerengine.tasks.container;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.util.helpers.ContainerAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walk to nearby storage containers one at a time and act on each — the shared shape of `chests`,
 * `withdraw` and `deposit`.
 *
 * <p>The companion goes to every container it uses, as a player would. It could read a chest from
 * across the room, but a companion that knows what is in every chest without opening one is a step
 * past player parity, and walking over is also what lets the owner see what it is doing.
 *
 * <p>Nothing here can hang. A container that cannot be reached in {@link #REACH_TIMEOUT_MS} is skipped
 * and named in the result, and running out of containers ends the task rather than waiting for one to
 * appear.
 */
public abstract class VisitContainersTask extends Task {
   private static final long REACH_TIMEOUT_MS = 30_000L;
   /** Ticks to stand at a container with the lid up, so the visit can be seen. */
   private static final int DWELL_TICKS = 10;

   private List<BlockPos> queue = List.of();
   private int index;
   private long headingSince;
   private int dwell;
   private boolean finishing;
   private boolean done;
   private BlockPos lidOpen;
   protected final List<BlockPos> unreachable = new ArrayList<>();

   /** Choose and order which containers to visit, from all of them within range, nearest first. */
   protected abstract List<BlockPos> plan(List<BlockPos> nearestFirst);

   /**
    * Do this task's work at one container, which is in reach.
    *
    * @return true when the whole job is done and no further container needs visiting
    */
   protected abstract boolean visit(BlockPos pos, Container container);

   /**
    * Every planned container has been visited. Return a task to run first (and then {@link #replan()}),
    * or null to finish.
    */
   protected Task whenOutOfContainers() {
      return null;
   }

   /** Look for containers again, for after one has been placed. */
   protected void replan() {
      this.queue = this.plan(ContainerAccess.findNearby(this.controller, ContainerAccess.SEARCH_RADIUS));
      this.index = 0;
      this.headingSince = System.currentTimeMillis();
   }

   @Override
   protected void onStart() {
      this.unreachable.clear();
      this.dwell = 0;
      this.finishing = false;
      this.done = false;
      this.replan();
   }

   @Override
   protected Task onTick() {
      if (this.done) {
         return null;
      }
      if (this.dwell > 0) {
         if (--this.dwell == 0) {
            this.closeLid();
            this.done = this.finishing;
         }
         return null;
      }
      if (this.index >= this.queue.size()) {
         Task first = this.whenOutOfContainers();
         // A subclass that called replan() has new containers to visit; otherwise there is nothing left.
         if (first == null && this.index >= this.queue.size()) {
            this.done = true;
         }
         return first;
      }

      BlockPos pos = this.queue.get(this.index);
      Level level = this.controller.getWorld();
      Optional<Container> container = ContainerAccess.open(level, pos);
      if (container.isEmpty()) {
         this.next(); // broken or moved since the plan
         return null;
      }
      if (!ContainerAccess.inReach(this.controller, pos)) {
         if (System.currentTimeMillis() - this.headingSince > REACH_TIMEOUT_MS) {
            this.unreachable.add(pos);
            this.next();
            return null;
         }
         this.setDebugState("Going to the " + ContainerAccess.describe(level, pos) + " at " + pos.toShortString());
         return new GetToBlockTask(pos);
      }

      this.setDebugState("Using the " + ContainerAccess.describe(level, pos) + " at " + pos.toShortString());
      this.openLid(pos);
      this.controller.getItemStorage().containers.WritableCache(this.controller, pos);
      this.finishing = this.visit(pos, container.get());
      this.controller.getItemStorage().registerSlotAction();
      this.dwell = DWELL_TICKS;
      this.next();
      return null;
   }

   private void next() {
      this.index++;
      this.headingSince = System.currentTimeMillis();
   }

   @Override
   public boolean isFinished() {
      return this.done;
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.closeLid();
   }

   private void openLid(BlockPos pos) {
      Level level = this.controller.getWorld();
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock) {
         level.blockEvent(pos, state.getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos) + 1);
         level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
         this.lidOpen = pos;
      }
   }

   private void closeLid() {
      if (this.lidOpen == null) {
         return;
      }
      BlockPos pos = this.lidOpen;
      this.lidOpen = null;
      Level level = this.controller.getWorld();
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof ChestBlock) {
         // Back to however many PLAYERS have it open, so a player looking in keeps their lid up.
         level.blockEvent(pos, state.getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos));
         level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 1.0F);
      }
   }

   /** "(x, y, z)" list of containers that could not be reached, or empty. */
   protected String unreachableNote() {
      if (this.unreachable.isEmpty()) {
         return "";
      }
      return " Could not reach " + this.unreachable.size() + " container(s) at "
         + String.join(", ", this.unreachable.stream().map(BlockPos::toShortString).toList()) + ".";
   }
}
