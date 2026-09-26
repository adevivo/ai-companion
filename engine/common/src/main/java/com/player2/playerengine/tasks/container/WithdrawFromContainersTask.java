package com.player2.playerengine.tasks.container;

import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ContainerAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

/**
 * `withdraw`: take items out of nearby containers, nearest first, until the count is met or every
 * container in range has been tried.
 *
 * <p>The count is how many to TAKE, not how many to end up holding: "withdraw wool 3" while carrying 2
 * takes 3 more. That is what the words say, and it is the opposite of `get`, which counts what is held.
 */
public class WithdrawFromContainersTask extends VisitContainersTask {
   /** Take every matching item in range. */
   public static final int ALL = -1;

   private final ItemTarget target;
   private final String name;
   private final int wanted;
   private int taken;
   private int searched;
   private boolean inventoryFull;
   private final List<String> takenFrom = new ArrayList<>();

   /** @param name what the agent called the item, used in the reply */
   public WithdrawFromContainersTask(ItemTarget target, String name, int count) {
      this.target = target;
      this.name = name;
      this.wanted = count;
   }

   private int remaining() {
      return this.wanted == ALL ? Integer.MAX_VALUE : this.wanted - this.taken;
   }

   @Override
   protected List<BlockPos> plan(List<BlockPos> nearestFirst) {
      // Containers already seen WITH the item first, then unseen ones, and those seen without it last —
      // a player could have put some in since, so they are still worth a look before giving up.
      List<BlockPos> has = new ArrayList<>();
      List<BlockPos> unknown = new ArrayList<>();
      List<BlockPos> hasNot = new ArrayList<>();
      for (BlockPos pos : nearestFirst) {
         Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
         if (cache.isEmpty()) {
            unknown.add(pos);
         } else if (cache.get().hasItem(this.target.getMatches())) {
            has.add(pos);
         } else {
            hasNot.add(pos);
         }
      }
      List<BlockPos> order = new ArrayList<>(has);
      order.addAll(unknown);
      order.addAll(hasNot);
      return order;
   }

   @Override
   protected boolean visit(BlockPos pos, Container container) {
      this.searched++;
      int available = ContainerAccess.count(container, this.target::matches);
      if (available == 0) {
         return false;
      }
      int want = Math.min(available, this.remaining());
      int moved = ContainerAccess.take(
         container, ((IInventoryProvider)this.controller.getEntity()).getLivingInventory(), this.target::matches, want
      );
      if (moved > 0) {
         this.taken += moved;
         this.takenFrom.add(moved + " " + this.name + " from the " + ContainerAccess.describe(this.controller.getWorld(), pos) + " at (" + pos.toShortString() + ")");
      }
      if (moved < want) {
         this.inventoryFull = true;
         return true;
      }
      return this.remaining() <= 0;
   }

   /** How it went, for the agent — and whether it fell short. */
   public Outcome outcome() {
      String name = this.name();
      String got = this.takenFrom.isEmpty() ? "" : "Took " + String.join(", ", this.takenFrom) + ".";
      if (this.inventoryFull) {
         return new Outcome(false, got + " Stopped because the inventory is FULL after taking " + this.taken + " " + name
            + ". Use `deposit` to make room first." + this.unreachableNote());
      }
      if (this.wanted == ALL) {
         return this.taken > 0
            ? new Outcome(true, got + " That was all the " + name + " in the " + this.searched + " container(s) within reach." + this.unreachableNote())
            : new Outcome(false, "There is no " + name + " in the " + this.searched + " container(s) within "
               + ContainerAccess.SEARCH_RADIUS + " blocks." + this.unreachableNote());
      }
      if (this.taken >= this.wanted) {
         return new Outcome(true, got + this.unreachableNote());
      }
      if (this.searched == 0 && this.unreachable.isEmpty()) {
         return new Outcome(false, "There are no chests, barrels or shulker boxes within " + ContainerAccess.SEARCH_RADIUS + " blocks to take " + name + " from.");
      }
      return new Outcome(false, (got.isEmpty() ? "" : got + " ") + "Found only " + this.taken + " of the " + this.wanted + " " + name
         + " asked for in the " + this.searched + " container(s) within " + ContainerAccess.SEARCH_RADIUS + " blocks." + this.unreachableNote()
         + " Use `get` to gather the rest, or ask the owner.");
   }

   private String name() {
      return this.name;
   }

   public record Outcome(boolean success, String message) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof WithdrawFromContainersTask task && task.target.equals(this.target) && task.wanted == this.wanted;
   }

   @Override
   protected String toDebugString() {
      return "Withdrawing " + (this.wanted == ALL ? "all" : this.wanted) + " " + this.name() + " from nearby containers";
   }
}
