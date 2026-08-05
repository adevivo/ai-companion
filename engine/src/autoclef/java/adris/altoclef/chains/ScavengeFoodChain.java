package adris.altoclef.chains;

import adris.altoclef.AltoClefController;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.tasks.movement.GetToEntityTask;
import adris.altoclef.tasksystem.TaskRunner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import java.util.List;

/**
 * Walk over and pick up food lying on the ground nearby.
 *
 * <p>A companion only collects what it physically stands on — {@code CompanionEntity#pickupItems}
 * sweeps a 2x1x2 box, which is about a player's own reach and means anything that lands more than a
 * step away stays where it fell. Mob drops scatter several blocks, so a companion could kill a herd of
 * pigs, walk off, and starve later beside the pork it earned. Nothing else in the mod ever goes to
 * fetch a dropped item unless the owner asked for that exact item by name.
 *
 * <p>Deliberately narrow. It only picks up <em>food</em>, only within {@link BehaviorConfig#scavengeRadius}
 * blocks, only while nothing is hunting the companion, and only when it has somewhere to put it. It
 * also sits below {@code UserTaskChain} so it can never interrupt a job — it fills the gap after the
 * work is finished, which is exactly when a kill has left meat on the floor.
 */
public class ScavengeFoodChain extends SingleTaskChain {

   /**
    * Below {@code UserTaskChain}'s 50, so tidying up never preempts what the owner actually asked for,
    * and well below the defence chain so it is not done under fire.
    */
   private static final float PRIORITY = 48.0F;

   /** The drop currently being fetched, so the chain does not re-target every tick as it walks. */
   private ItemEntity target;

   public ScavengeFoodChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
      this.target = null;
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   public String getName() {
      return "Scavenge food";
   }

   @Override
   public float getPriority() {
      if (this.controller == null || !BehaviorConfig.scavengeFood) {
         this.clearTarget();
         return Float.NEGATIVE_INFINITY;
      }

      LivingEntity self = this.controller.getEntity();
      if (self == null) {
         this.clearTarget();
         return Float.NEGATIVE_INFINITY;
      }

      // Never scavenge under fire. Walking to a drop means walking away from cover and toward wherever
      // the fight happened to leave it, which is the last thing a companion should be doing while
      // something is still shooting at it.
      if (!this.controller.getEntityTracker().getHostiles().isEmpty()) {
         this.clearTarget();
         return Float.NEGATIVE_INFINITY;
      }

      // Somewhere to put it. Full inventories are handled by not bothering rather than by dropping
      // something else — a companion deciding for itself what to throw away is a separate argument.
      if (this.controller.getInventory().getEmptySlot() < 0) {
         this.clearTarget();
         return Float.NEGATIVE_INFINITY;
      }

      if (this.target != null && !isCollectable(this.target)) {
         this.target = null; // picked up, despawned, or someone else got it
      }
      if (this.target == null) {
         this.target = findNearestFood(self);
      }
      if (this.target == null) {
         this.clearTarget();
         return Float.NEGATIVE_INFINITY;
      }

      // closeEnoughDistance of 1 rather than 0: arriving on top of it is what lets the entity's own
      // pickup sweep take it, and demanding an exact block match makes it fidget next to floating items.
      this.setTask(new GetToEntityTask(this.target, 1.0));
      return PRIORITY;
   }

   /** Clear any in-flight fetch. Safe to call every tick. Not {@code stop()} — that is TaskChain's. */
   private void clearTarget() {
      this.target = null;
      if (this.mainTask != null) {
         this.setTask(null);
      }
   }

   /** The closest edible drop within the configured radius, or null if there is nothing worth walking to. */
   private ItemEntity findNearestFood(LivingEntity self) {
      double radius = Math.max(1.0, BehaviorConfig.scavengeRadius);
      AABB box = self.getBoundingBox().inflate(radius);
      List<ItemEntity> drops = self.level().getEntitiesOfClass(ItemEntity.class, box,
            ScavengeFoodChain::isCollectable);

      ItemEntity best = null;
      double bestDistance = Double.MAX_VALUE;
      for (ItemEntity drop : drops) {
         double distance = drop.distanceToSqr(self);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = drop;
         }
      }
      return best;
   }

   /**
    * Whether a drop is worth walking to: still there, edible, and not a spider eye.
    *
    * <p>Spider eyes are excluded for the same reason {@code FoodChain} refuses to eat them — Poison
    * damages any {@code LivingEntity}, with none of the player-only gating that makes rotten flesh
    * harmless here. Rotten flesh is deliberately included: it is free food for a companion and it is
    * most of what the things it fights leave behind.
    */
   private static boolean isCollectable(ItemEntity drop) {
      if (drop == null || drop.isRemoved() || !drop.isAlive()) {
         return false;
      }
      ItemStack stack = drop.getItem();
      return !stack.isEmpty() && ItemVer.isFood(stack) && !stack.is(Items.SPIDER_EYE);
   }
}
