package com.player2.playerengine.tasks.container;

import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.StorageHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * `get`: take what the owner's allowed containers already hold, then gather the rest.
 *
 * <p>Before this, `get` never looked in chests, so a companion asked for wool went hunting sheep past a
 * chest full of it. Only containers the owner allowed are used (see {@code ChestPermissions}), and ones
 * already seen without the item are skipped, since gathering it is the fallback anyway.
 *
 * <p>The targets are absolute counts (what the companion should end up carrying), as
 * {@code AgentCommandUtils.addPresentItemsToTargets} makes them.
 */
public class GetFromStorageFirstTask extends Task {
   private final ItemTarget[] targets;
   private final Task gather;
   private final List<String> fromStorage = new ArrayList<>();
   private int index;
   private WithdrawFromContainersTask withdrawing;

   public GetFromStorageFirstTask(ItemTarget[] targets, Task gather) {
      this.targets = targets;
      this.gather = gather;
   }

   @Override
   protected void onStart() {
      this.index = 0;
      this.withdrawing = null;
      this.fromStorage.clear();
   }

   @Override
   protected Task onTick() {
      if (this.withdrawing != null) {
         if (!this.withdrawing.isFinished()) {
            return this.withdrawing;
         }
         if (this.withdrawing.taken() > 0) {
            this.fromStorage.add(this.withdrawing.taken() + " " + label(this.targets[this.index]));
         }
         this.withdrawing = null;
         this.index++;
      }
      while (this.index < this.targets.length) {
         ItemTarget target = this.targets[this.index];
         int need = target.getTargetCount() - this.controller.getItemStorage().getItemCountInventoryOnly(target.getMatches());
         if (need > 0) {
            this.setDebugState("Checking allowed containers for " + label(target));
            this.withdrawing = new WithdrawFromContainersTask(target, label(target), need, true);
            return this.withdrawing;
         }
         this.index++;
      }
      this.setDebugState("Gathering the rest");
      return this.gather;
   }

   @Override
   public boolean isFinished() {
      // Checked here rather than asked of the gather task: a ResourceTask reads its controller in
      // isFinished(), and has none until it has been started, which it may never be.
      return this.controller != null && this.index >= this.targets.length && this.withdrawing == null
         && StorageHelper.itemTargetsMet(this.controller, this.targets);
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   /** What came out of containers, for the agent, or empty when nothing did. */
   public String storageReport() {
      return this.fromStorage.isEmpty() ? "" : "Took " + String.join(", ", this.fromStorage) + " from your owner's containers.";
   }

   private static String label(ItemTarget target) {
      return target.isCatalogueItem() ? target.getCatalogueName() : ItemHelper.stripItemName(target.getMatches()[0]);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetFromStorageFirstTask task && java.util.Arrays.equals(task.targets, this.targets);
   }

   @Override
   protected String toDebugString() {
      return "Getting " + java.util.Arrays.toString(this.targets) + ", from allowed containers first";
   }
}
