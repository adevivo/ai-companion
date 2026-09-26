package com.player2.playerengine.tasks.container;

import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.automaton.api.entity.IInventoryProvider;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.construction.PlaceBlockNearbyTask;
import com.player2.playerengine.trackers.storage.ContainerCache;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ContainerAccess;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * `deposit`: put exactly the given counts into nearby containers, filling the next one when a container
 * is full. When there is nowhere to put them, place a chest — crafting one first if none is carried —
 * once, and then give up rather than loop.
 *
 * <p>Replaces {@code StoreInAnyContainerTask} for the command. That task counted what was already in
 * the chest toward the deposit, so "deposit diamond 2" into a chest holding 5 moved nothing, and it only
 * finished once NONE of the item was carried, so depositing part of a stack never ended.
 */
public class DepositInContainersTask extends VisitContainersTask {
   private final Map<ItemTarget, Integer> remaining = new LinkedHashMap<>();
   private final Map<ItemTarget, Integer> requested = new LinkedHashMap<>();
   private final List<String> putIn = new ArrayList<>();
   private boolean placedContainer;
   private PlaceBlockNearbyTask placeTask;

   public DepositInContainersTask(ItemTarget... toStore) {
      for (ItemTarget target : toStore) {
         this.remaining.merge(target, target.getTargetCount(), Integer::sum);
      }
      this.requested.putAll(this.remaining);
   }

   private boolean nothingLeft() {
      return this.remaining.entrySet().stream()
         .allMatch(e -> e.getValue() <= 0 || this.controller.getItemStorage().getItemCountInventoryOnly(e.getKey().getMatches()) == 0);
   }

   @Override
   protected List<BlockPos> plan(List<BlockPos> nearestFirst) {
      List<BlockPos> order = new ArrayList<>();
      for (BlockPos pos : nearestFirst) {
         Optional<ContainerCache> cache = this.controller.getItemStorage().getContainerAtPosition(pos);
         if (cache.isEmpty() || !cache.get().isFull()) {
            order.add(pos);
         }
      }
      return order;
   }

   @Override
   protected boolean visit(BlockPos pos, Container container) {
      int movedHere = 0;
      for (Map.Entry<ItemTarget, Integer> e : this.remaining.entrySet()) {
         if (e.getValue() <= 0) {
            continue;
         }
         ItemTarget target = e.getKey();
         int moved = ContainerAccess.put(
            ((IInventoryProvider)this.controller.getEntity()).getLivingInventory(), container, s -> target.matches(s.getItem()), e.getValue()
         );
         e.setValue(e.getValue() - moved);
         movedHere += moved;
      }
      if (movedHere > 0) {
         this.putIn.add(movedHere + " into the " + ContainerAccess.describe(this.controller.getWorld(), pos) + " at (" + pos.toShortString() + ")");
      }
      return this.nothingLeft();
   }

   @Override
   protected Task whenOutOfContainers() {
      if (this.nothingLeft() || this.placedContainer) {
         return null;
      }
      if (this.placeTask != null) {
         if (!this.placeTask.isFinished()) {
            return this.placeTask;
         }
         this.placedContainer = true;
         this.replan();
         return null;
      }
      if (!this.controller.getItemStorage().hasItem(Items.CHEST, Items.BARREL)) {
         this.setDebugState("No container with room nearby; getting a chest.");
         return TaskCatalogue.getItemTask(Items.CHEST, 1);
      }
      this.setDebugState("No container with room nearby; placing one.");
      this.placeTask = new PlaceBlockNearbyTask(
         pos -> !WorldHelper.isChest(this.controller, pos) || WorldHelper.isAir(this.controller, pos.above()), Blocks.CHEST, Blocks.BARREL
      );
      return this.placeTask;
   }

   /** How it went, for the agent — and whether it fell short. */
   public WithdrawFromContainersTask.Outcome outcome() {
      String done = this.putIn.isEmpty() ? "" : "Put " + String.join(", ", this.putIn) + ".";
      List<String> shortOf = new ArrayList<>();
      for (Map.Entry<ItemTarget, Integer> e : this.remaining.entrySet()) {
         int left = e.getValue();
         if (left > 0) {
            int carried = this.controller.getItemStorage().getItemCountInventoryOnly(e.getKey().getMatches());
            // Left over only because it ran out of that item is not a shortfall — the count asked for
            // came from a snapshot the agent saw a turn ago.
            if (carried > 0) {
               shortOf.add(left + " " + label(e.getKey()));
            }
         }
      }
      if (shortOf.isEmpty()) {
         return new WithdrawFromContainersTask.Outcome(true, (done.isEmpty() ? "Nothing needed depositing." : done) + this.unreachableNote());
      }
      return new WithdrawFromContainersTask.Outcome(false, (done.isEmpty() ? "" : done + " ") + "Could not store "
         + String.join(", ", shortOf) + ": every container within " + ContainerAccess.SEARCH_RADIUS
         + " blocks is full or out of reach." + this.unreachableNote() + " Place another chest, or `give` the items to the owner.");
   }

   private static String label(ItemTarget target) {
      return target.isCatalogueItem()
         ? target.getCatalogueName()
         : String.join("/", Arrays.stream(target.getMatches()).map(ItemHelper::stripItemName).toList());
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof DepositInContainersTask task && task.requested.equals(this.requested);
   }

   @Override
   protected String toDebugString() {
      return "Depositing " + this.requested.size() + " kind(s) of item into nearby containers";
   }
}
