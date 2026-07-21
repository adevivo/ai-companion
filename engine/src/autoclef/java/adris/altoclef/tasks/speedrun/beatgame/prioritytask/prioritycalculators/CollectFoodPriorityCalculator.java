package adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

import adris.altoclef.AltoClefController;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.Slot;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.state.BlockState;

public class CollectFoodPriorityCalculator extends ItemPriorityCalculator {
   private final AltoClefController mod;
   private final double foodUnits;

   public CollectFoodPriorityCalculator(AltoClefController mod, double foodUnits) {
      super(Integer.MAX_VALUE, Integer.MAX_VALUE);
      this.mod = mod;
      this.foodUnits = foodUnits;
   }

   @Override
   public double calculatePriority(int count) {
      double distance = this.getDistance(this.mod);
      double multiplier = 1.0;
      double foodPotential = CollectFoodTask.calculateFoodPotential(this.mod);
      if (Double.isInfinite(distance) && foodPotential < this.foodUnits) {
         return 0.1;
      } else {
         Optional<BlockPos> hay = this.mod.getBlockScanner().getNearestBlock(Blocks.HAY_BLOCK);
         if (hay.isPresent() && WorldHelper.inRangeXZ(hay.get(), this.mod.getPlayer().blockPosition(), 75.0)
            || this.mod.getEntityTracker().itemDropped(Items.HAY_BLOCK)) {
            multiplier = 50.0;
         }

         if (foodPotential > this.foodUnits) {
            if (foodPotential > this.foodUnits + 20.0) {
               return Double.NEGATIVE_INFINITY;
            } else {
               return distance > 10.0 && hay.isEmpty() ? Double.NEGATIVE_INFINITY : 17.0 / distance * 30.0 / count / 2.0 * multiplier;
            }
         } else {
            if (foodPotential < 10.0) {
               multiplier = Math.max(11.0 / foodPotential, 22.0);
            }

            return 33.0 / distance * 37.0 * multiplier;
         }
      }
   }

   private double getDistance(AltoClefController mod) {
      LivingEntity clientPlayerEntity = mod.getPlayer();

      for (Item item : CollectFoodTask.ITEMS_TO_PICK_UP) {
         double dist = this.pickupTaskOrNull(mod, item);
         if (dist != Double.NEGATIVE_INFINITY) {
            return dist;
         }
      }

      for (CollectFoodTask.CookableFoodTarget cookable : CollectFoodTask.COOKABLE_FOODS) {
         double dist = this.pickupTaskOrNull(mod, cookable.getRaw(), 20.0);
         if (dist == Double.NEGATIVE_INFINITY) {
            dist = this.pickupTaskOrNull(mod, cookable.getCooked(), 40.0);
         }

         if (dist != Double.NEGATIVE_INFINITY) {
            return dist;
         }
      }

      double hayTaskBlock = this.pickupBlockTaskOrNull(mod, Blocks.HAY_BLOCK, Items.HAY_BLOCK, 300.0);
      if (hayTaskBlock != Double.NEGATIVE_INFINITY) {
         return hayTaskBlock;
      } else {
         for (CollectFoodTask.CropTarget target : CollectFoodTask.CROPS) {
            double t = this.pickupBlockTaskOrNull(mod, target.cropBlock, target.cropItem, blockPos -> {
               BlockState s = mod.getWorld().getBlockState(blockPos);
               Block b = s.getBlock();
               if (b instanceof CropBlock) {
                  boolean isWheat = !(b instanceof PotatoBlock) && !(b instanceof CarrotBlock) && !(b instanceof BeetrootBlock);
                  if (isWheat) {
                     if (!mod.getChunkTracker().isChunkLoaded(blockPos)) {
                        return false;
                     }

                     CropBlock crop = (CropBlock)b;
                     return crop.isMaxAge(s);
                  }
               }

               return WorldHelper.canBreak(mod, blockPos);
            }, 96.0);
            if (t != Double.NEGATIVE_INFINITY) {
               return t;
            }
         }

         double bestScore = 0.0;
         Entity bestEntity = null;
         Predicate<Entity> notBaby = entity -> {
            if (entity instanceof LivingEntity livingEntity && !livingEntity.isBaby()) {
            }

            return false;
         };

         for (CollectFoodTask.CookableFoodTarget cookable : CollectFoodTask.COOKABLE_FOODS) {
            if (mod.getEntityTracker().entityFound(cookable.mobToKill)) {
               Optional<Entity> nearest = mod.getEntityTracker().getClosestEntity(mod.getPlayer().position(), notBaby, cookable.mobToKill);
               if (!nearest.isEmpty()) {
                  int hungerPerformance = cookable.getCookedUnits();
                  double sqDistance = nearest.get().distanceToSqr(mod.getPlayer());
                  double score = 100.0 * hungerPerformance / sqDistance;
                  if (score > bestScore) {
                     bestScore = score;
                     bestEntity = nearest.get();
                  }
               }
            }
         }

         if (bestEntity != null) {
            return bestEntity.distanceTo(clientPlayerEntity);
         } else {
            double berryPickup = this.pickupBlockTaskOrNull(mod, Blocks.SWEET_BERRY_BUSH, Items.SWEET_BERRIES, 96.0);
            return berryPickup != Double.NEGATIVE_INFINITY ? berryPickup : Double.POSITIVE_INFINITY;
         }
      }
   }

   private double pickupBlockTaskOrNull(AltoClefController mod, Block blockToCheck, Item itemToGrab, double maxRange) {
      return this.pickupBlockTaskOrNull(mod, blockToCheck, itemToGrab, toAccept -> true, maxRange);
   }

   private double pickupBlockTaskOrNull(AltoClefController mod, Block blockToCheck, Item itemToGrab, Predicate<BlockPos> accept, double maxRange) {
      Predicate<BlockPos> acceptPlus = blockPos -> !WorldHelper.canBreak(mod, blockPos) ? false : accept.test(blockPos);
      Optional<BlockPos> nearestBlock = mod.getBlockScanner().getNearestBlock(mod.getPlayer().position(), acceptPlus, blockToCheck);
      if (nearestBlock.isPresent() && !nearestBlock.get().closerToCenterThan(mod.getPlayer().position(), maxRange)) {
         nearestBlock = Optional.empty();
      }

      Optional<ItemEntity> nearestDrop = Optional.empty();
      if (mod.getEntityTracker().itemDropped(itemToGrab)) {
         nearestDrop = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), itemToGrab);
      }

      if (nearestDrop.isPresent()) {
         return nearestDrop.get().distanceTo(mod.getPlayer());
      } else {
         return nearestBlock.isPresent() ? Math.sqrt(mod.getPlayer().distanceToSqr(WorldHelper.toVec3d(nearestBlock.get()))) : Double.NEGATIVE_INFINITY;
      }
   }

   private double pickupTaskOrNull(AltoClefController mod, Item itemToGrab) {
      return this.pickupTaskOrNull(mod, itemToGrab, Double.POSITIVE_INFINITY);
   }

   private double pickupTaskOrNull(AltoClefController mod, Item itemToGrab, double maxRange) {
      Optional<ItemEntity> nearestDrop = Optional.empty();
      if (mod.getEntityTracker().itemDropped(itemToGrab)) {
         nearestDrop = mod.getEntityTracker().getClosestItemDrop(mod.getPlayer().position(), itemToGrab);
      }

      if (nearestDrop.isPresent() && nearestDrop.get().closerThan(mod.getPlayer(), maxRange)) {
         if (mod.getItemStorage().getSlotsThatCanFitInPlayerInventory(nearestDrop.get().getItem(), false).isEmpty()) {
            Optional<Slot> slot = StorageHelper.getGarbageSlot(mod);
            if (slot.isPresent()) {
               ItemStack stack = StorageHelper.getItemStackInSlot(slot.get());
               if (ItemVer.isFood(stack.getItem())) {
                  int inventoryCost = ItemVer.getFoodComponent(stack.getItem()).getHunger() * stack.getCount();
                  double hunger = 0.0;
                  if (ItemVer.isFood(itemToGrab)) {
                     hunger = ItemVer.getFoodComponent(itemToGrab).getHunger();
                  } else if (itemToGrab.equals(Items.WHEAT)) {
                     hunger += ItemVer.getFoodComponent(Items.BREAD).getHunger() / 3.0;
                  } else {
                     mod.log("unknown food item: " + itemToGrab);
                  }

                  int groundCost = (int)(hunger * nearestDrop.get().getItem().getCount());
                  if (inventoryCost > groundCost) {
                     return Double.NEGATIVE_INFINITY;
                  }
               }
            }
         }

         return nearestDrop.get().distanceTo(mod.getPlayer());
      } else {
         return Double.NEGATIVE_INFINITY;
      }
   }
}
