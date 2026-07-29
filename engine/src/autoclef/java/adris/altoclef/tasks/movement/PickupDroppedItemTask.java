package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasks.AbstractDoToClosestObjectTask;
import adris.altoclef.tasks.resources.SatisfyMiningRequirementTask;
import adris.altoclef.tasks.slot.EnsureFreeInventorySlotTask;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.StlHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.phys.Vec3;

public class PickupDroppedItemTask extends AbstractDoToClosestObjectTask<ItemEntity> implements ITaskRequiresGrounded {
   private static final Task getPickaxeFirstTask = new SatisfyMiningRequirementTask(MiningRequirement.STONE);
   private static boolean isGettingPickaxeFirstFlag = false;
   private final TimeoutWanderTask wanderTask = new TimeoutWanderTask(5.0F, true);
   private final MovementProgressChecker stuckCheck = new MovementProgressChecker();
   private final MovementProgressChecker progressChecker = new MovementProgressChecker();
   private final ItemTarget[] itemTargets;
   private final Set<ItemEntity> blacklist = new HashSet<>();
   private final boolean freeInventoryIfFull;
   Block[] annoyingBlocks = new Block[]{
      Blocks.VINE,
      Blocks.NETHER_SPROUTS,
      Blocks.CAVE_VINES,
      Blocks.CAVE_VINES_PLANT,
      Blocks.TWISTING_VINES,
      Blocks.TWISTING_VINES_PLANT,
      Blocks.WEEPING_VINES_PLANT,
      Blocks.LADDER,
      Blocks.BIG_DRIPLEAF,
      Blocks.BIG_DRIPLEAF_STEM,
      Blocks.SMALL_DRIPLEAF,
      Blocks.TALL_GRASS,
      Blocks.GRASS
   };
   private Task unstuckTask = null;
   private AltoClefController mod;
   private boolean collectingPickaxeForThisResource = false;
   private ItemEntity currentDrop = null;

   public PickupDroppedItemTask(ItemTarget[] itemTargets, boolean freeInventoryIfFull) {
      this.itemTargets = itemTargets;
      this.freeInventoryIfFull = freeInventoryIfFull;
   }

   public PickupDroppedItemTask(ItemTarget target, boolean freeInventoryIfFull) {
      this(new ItemTarget[]{target}, freeInventoryIfFull);
   }

   public PickupDroppedItemTask(Item item, int targetCount, boolean freeInventoryIfFull) {
      this(new ItemTarget(item, targetCount), freeInventoryIfFull);
   }

   public PickupDroppedItemTask(Item item, int targetCount) {
      this(item, targetCount, true);
   }

   private static BlockPos[] generateSides(BlockPos pos) {
      return new BlockPos[]{
         pos.offset(1, 0, 0),
         pos.offset(-1, 0, 0),
         pos.offset(0, 0, 1),
         pos.offset(0, 0, -1),
         pos.offset(1, 0, -1),
         pos.offset(1, 0, 1),
         pos.offset(-1, 0, -1),
         pos.offset(-1, 0, 1)
      };
   }

   public static boolean isIsGettingPickaxeFirst(AltoClefController mod) {
      return isGettingPickaxeFirstFlag && mod.getModSettings().shouldCollectPickaxeFirst();
   }

   private boolean isAnnoying(AltoClefController mod, BlockPos pos) {
      if (this.annoyingBlocks != null) {
         Block[] var3 = this.annoyingBlocks;
         int var4 = var3.length;
         byte var5 = 0;
         if (var5 < var4) {
            Block AnnoyingBlocks = var3[var5];
            return mod.getWorld().getBlockState(pos).getBlock() == AnnoyingBlocks
               || mod.getWorld().getBlockState(pos).getBlock() instanceof DoorBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FenceGateBlock
               || mod.getWorld().getBlockState(pos).getBlock() instanceof FlowerBlock;
         }
      }

      return false;
   }

   private BlockPos stuckInBlock(AltoClefController mod) {
      BlockPos p = mod.getPlayer().blockPosition();
      if (this.isAnnoying(mod, p)) {
         return p;
      } else if (this.isAnnoying(mod, p.above())) {
         return p.above();
      } else {
         BlockPos[] toCheck = generateSides(p);

         for (BlockPos check : toCheck) {
            if (this.isAnnoying(mod, check)) {
               return check;
            }
         }

         BlockPos[] toCheckHigh = generateSides(p.above());

         for (BlockPos checkx : toCheckHigh) {
            if (this.isAnnoying(mod, checkx)) {
               return checkx;
            }
         }

         return null;
      }
   }

   private Task getFenceUnstuckTask() {
      return new SafeRandomShimmyTask();
   }

   public boolean isCollectingPickaxeForThis() {
      return this.collectingPickaxeForThisResource;
   }

   @Override
   protected void onStart() {
      this.wanderTask.reset();
      this.progressChecker.reset();
      this.stuckCheck.reset();
      this.consecutiveFailures = 0;
      this.heldBefore = collectedCount();
   }

   /** How many of the items this task is after are currently held — the only reliable sign of progress. */
   private int collectedCount() {
      int total = 0;
      for (ItemTarget target : this.itemTargets) {
         total += this.controller.getItemStorage().getItemCount(target.getMatches());
      }
      return total;
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected Task onTick() {
      if (this.wanderTask.isActive() && !this.wanderTask.isFinished()) {
         this.setDebugState("Wandering.");
         return this.wanderTask;
      } else {
         AltoClefController mod = this.controller;
         // Anything actually collected means the run of failures is over, whichever drop it came from.
         int held = collectedCount();
         if (held > this.heldBefore) {
            this.consecutiveFailures = 0;
         }
         this.heldBefore = held;
         if (mod.getBaritone().getPathingBehavior().isPathing()) {
            this.progressChecker.reset();
         }

         if (this.unstuckTask != null && this.unstuckTask.isActive() && !this.unstuckTask.isFinished() && this.stuckInBlock(mod) != null) {
            this.setDebugState("Getting unstuck from block.");
            this.stuckCheck.reset();
            mod.getBaritone().getCustomGoalProcess().onLostControl();
            mod.getBaritone().getExploreProcess().onLostControl();
            return this.unstuckTask;
         } else {
            if (!this.progressChecker.check(mod) || !this.stuckCheck.check(mod)) {
               BlockPos blockStuck = this.stuckInBlock(mod);
               if (blockStuck != null) {
                  this.unstuckTask = this.getFenceUnstuckTask();
                  return this.unstuckTask;
               }

               this.stuckCheck.reset();
            }

            this.mod = mod;
            if (isIsGettingPickaxeFirst(mod)
               && this.collectingPickaxeForThisResource
               && !StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.STONE)) {
               this.progressChecker.reset();
               this.setDebugState("Collecting pickaxe first");
               return getPickaxeFirstTask;
            } else {
               if (StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.STONE)) {
                  isGettingPickaxeFirstFlag = false;
               }

               this.collectingPickaxeForThisResource = false;
               if (!this.progressChecker.check(mod)) {
                  mod.getBaritone().getPathingBehavior().forceCancel();
                  if (this.currentDrop != null && !this.currentDrop.getItem().isEmpty()) {
                     if (!isGettingPickaxeFirstFlag
                        && mod.getModSettings().shouldCollectPickaxeFirst()
                        && !StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.STONE)) {
                        Debug.logMessage("Failed to pick up drop, will try to collect a stone pickaxe first and try again!");
                        this.collectingPickaxeForThisResource = true;
                        isGettingPickaxeFirstFlag = true;
                        return getPickaxeFirstTask;
                     }

                     Debug.logMessage(
                        StlHelper.toString(this.blacklist, element -> element == null ? "(null)" : element.getItem().getItem().getDescriptionId())
                     );
                     // "Unreachable" was the only explanation this ever gave, and for a full
                     // inventory it is simply false — the drop is right there and there is nowhere to
                     // put it. Naming the real cause is what lets the agent act (deposit/give)
                     // instead of re-issuing the same `get`, which it did four times in one session.
                     boolean noRoom = cannotFit(this.currentDrop);
                     Debug.logMessage(noRoom
                        ? "Failed to pick up drop: the inventory is full and it does not stack onto anything held."
                        : "Failed to pick up drop, suggesting it's unreachable.");
                     this.blacklist.add(this.currentDrop);
                     mod.getEntityTracker().requestEntityUnreachable(this.currentDrop);
                     reportRepeatedFailure(mod, noRoom);
                     return this.wanderTask;
                  }
               }

               return super.onTick();
            }
         }
      }
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof PickupDroppedItemTask task)
         ? false
         : Arrays.equals((Object[])task.itemTargets, (Object[])this.itemTargets) && task.freeInventoryIfFull == this.freeInventoryIfFull;
   }

   @Override
   protected String toDebugString() {
      StringBuilder result = new StringBuilder();
      result.append("Pickup Dropped Items: [");
      int c = 0;

      for (ItemTarget target : this.itemTargets) {
         result.append(target.toString());
         if (++c != this.itemTargets.length) {
            result.append(", ");
         }
      }

      result.append("]");
      return result.toString();
   }

   protected Vec3 getPos(AltoClefController mod, ItemEntity obj) {
      if (!obj.onGround() && !obj.isInWater()) {
         BlockPos p = obj.blockPosition();
         return !WorldHelper.isSolidBlock(this.controller, p.below(3)) ? obj.position().subtract(0.0, 2.0, 0.0) : obj.position().subtract(0.0, 1.0, 0.0);
      } else {
         return obj.position();
      }
   }

   @Override
   protected Optional<ItemEntity> getClosestTo(AltoClefController mod, Vec3 pos) {
      return mod.getEntityTracker().getClosestItemDrop(pos, this.itemTargets);
   }

   @Override
   protected Vec3 getOriginPos(AltoClefController mod) {
      return mod.getPlayer().position();
   }

   protected Task getGoalTask(ItemEntity itemEntity) {
      if (!itemEntity.equals(this.currentDrop)) {
         this.currentDrop = itemEntity;
         this.progressChecker.reset();
         if (isGettingPickaxeFirstFlag && this.collectingPickaxeForThisResource) {
            Debug.logMessage("New goal, no longer collecting a pickaxe.");
            this.collectingPickaxeForThisResource = false;
            isGettingPickaxeFirstFlag = false;
         }
      }

      // Whether the drop can fit is a fact about the inventory, not about proximity. This used to
      // also require colliding with the item, which gated the only escape from a full inventory on
      // the one thing a full inventory prevents: nothing is collected, so no collision is registered,
      // so no slot is ever freed. Measured 2026-07-29 — 3,814 consecutive failed pickups over 27
      // minutes with EnsureFreeInventorySlotTask never once reached.
      //
      // Still requires something throwable: with nothing to drop, that task sits on "All items are
      // protected" forever, which is the same silent spin in a new place. Falling through instead
      // walks to the drop, fails, and reports through the path above.
      if (this.freeInventoryIfFull && cannotFit(itemEntity)
            && StorageHelper.getGarbageSlot(this.mod).isPresent()) {
         this.setDebugState("Inventory is full; making room.");
         // setDebugState only surfaces in taskStatus, so this path used to leave nothing in the log at
         // all: confirming it had ever run meant diffing two /companion stats dumps for a vanished
         // stack. Logged once per entry — getGoalTask runs every tick while the condition holds.
         if (!this.announcedMakingRoom) {
            this.announcedMakingRoom = true;
            Debug.logMessage("Inventory is full and " + itemEntity.getItem().getItem().getDescriptionId()
                  + " will not fit; dropping something to make room.");
         }
         return new EnsureFreeInventorySlotTask();
      }
      this.announcedMakingRoom = false;
      return new GetToEntityTask(itemEntity);
   }

   /** Whether the "making room" line has already been logged for the current full-inventory episode. */
   private boolean announcedMakingRoom = false;

   /** Whether there is nowhere in the inventory for this drop to go — no free slot, no stack to merge into. */
   private boolean cannotFit(ItemEntity itemEntity) {
      return this.mod.getItemStorage().getSlotsThatCanFitInPlayerInventory(itemEntity.getItem(), false).isEmpty();
   }

   /**
    * Tell the owner and the agent once a run of failed pickups stops looking like bad luck.
    *
    * <p>Before this the whole failure was a {@code Debug.logMessage} and nothing else: one session
    * spent 27 minutes and 3,814 identical failures without a single word in chat, and the owner
    * eventually had to ask "did you get stuck?". {@link AltoClefController#logAgentNotice} reaches the
    * log, the agent's next turn and the owner together, which is what turns this into something
    * either of them can act on.
    *
    * <p>Reported once per run rather than per failure — the point is to break the silence, not to
    * replace a log flood with a chat flood. The counter resets whenever a pickup succeeds.
    */
   private void reportRepeatedFailure(AltoClefController mod, boolean noRoom) {
      if (++this.consecutiveFailures != FAILURES_BEFORE_REPORT) {
         return;
      }
      if (noRoom) {
         mod.logAgentNotice(
            "Could not pick up the items needed: the inventory is FULL and nothing being collected stacks "
               + "onto what is already held. Nothing will be collected until room is made — use `deposit` "
               + "into a nearby container, or `give` the owner something, then continue.",
            "I can't pick anything up — my inventory is full.");
      } else {
         mod.logAgentNotice(
            "Could not reach the items needed after repeated attempts; they may be behind terrain or in "
               + "water. Try moving somewhere else, or collecting a different material.",
            "I can't get to those items.");
      }
   }

   /**
    * How many failures in a row before saying so. Small — a couple of misses are normal while pathing
    * around a drop, and anything past that is the failure mode above rather than bad luck.
    */
   private static final int FAILURES_BEFORE_REPORT = 3;

   private int consecutiveFailures = 0;

   /** Held count of the targeted items as of last tick, to detect that something was collected. */
   private int heldBefore = 0;

   protected boolean isValid(AltoClefController mod, ItemEntity obj) {
      return obj.isAlive() && !this.blacklist.contains(obj);
   }
}
