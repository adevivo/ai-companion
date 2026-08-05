package adris.altoclef.chains;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.Settings;
import adris.altoclef.multiversion.FoodComponentWrapper;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ConfigHelper;
import adris.altoclef.util.helpers.WorldHelper;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FoodChain extends SingleTaskChain {
   private static FoodChain.FoodChainConfig config;
   private static boolean hasFood;
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private boolean isTryingToEat = false;
   private boolean requestFillup = false;
   private boolean needsToCollectFood = false;
   private Optional<Item> cachedPerfectFood = Optional.empty();
   private boolean shouldStop = false;

   public FoodChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(AltoClefController controller) {
   }

   /**
    * Begin (or continue) a meal.
    *
    * <p>This used to force {@code Input.CLICK_RIGHT} and let the interaction manager handle it, and
    * <b>it had never once fed a companion.</b> {@code LivingEntityInteractionManager.interactItem}
    * calls {@code stack.use(world, null, hand)} with a null player, and vanilla's {@code Item.use}
    * dereferences that argument immediately for anything edible — so the call threw on its first
    * instruction every time and the exception was swallowed by the surrounding try. The same dead path
    * is why {@code EatCommand} consumes directly against the inventory instead.
    *
    * <p>It went unnoticed for as long as it did because hunger could never fall (see
    * {@code LivingEntityHungerManager.tickCompanion}), so {@code needsToEat} was essentially never
    * true and nothing ever called this in anger.
    *
    * <p>Driving {@code startUsingItem} directly skips the broken {@code Item.use} entry point and
    * hands over to the machinery on the far side of it, which does work: {@code LivingEntity.tick}
    * runs the use timer, plays the eating sound and particles, and on completion calls
    * {@code finishUsingItem} → {@code eatFood}, which {@code CompanionEntity} overrides to actually
    * fill the bar. So the companion eats over the normal 32 ticks, visibly, rather than food vanishing
    * out of its pack.
    */
   private void startEat(AltoClefController controller, Item food) {
      controller.getSlotHandler().forceEquipItem(new ItemTarget(food), true);
      controller.getExtraBaritoneSettings().setInteractionPaused(true);
      this.isTryingToEat = true;
      this.requestFillup = true;

      LivingEntity entity = controller.getEntity();
      // Only kick off a new mouthful when it is not already mid-bite; calling this every tick would
      // restart the use timer forever and it would chew without ever swallowing.
      if (entity != null && !entity.isUsingItem() && entity.getMainHandItem().getItem() == food) {
         entity.startUsingItem(InteractionHand.MAIN_HAND);
         // Traceable on purpose: this chain was silent, so a playtest could show a companion starving
         // beside a full pack with no way to tell whether it had tried to eat and failed or never
         // tried at all. Internal log only — it must not reach chat or the model's context.
         Debug.logInternal("FoodChain: started eating " + food + " (food "
               + controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() + "/20)");
      }
   }

   private void stopEat(AltoClefController controller) {
      if (this.isTryingToEat) {
         controller.getExtraBaritoneSettings().setInteractionPaused(false);
         this.isTryingToEat = false;
         this.requestFillup = false;
         LivingEntity entity = controller.getEntity();
         // Drop a half-finished mouthful rather than leaving the use timer running into whatever the
         // companion does next — a raised item blocks the swing animation and the shield swap below.
         if (entity != null && entity.isUsingItem()) {
            entity.stopUsingItem();
         }
         if (controller.getItemStorage().hasItem(Items.SHIELD) && !controller.getItemStorage().hasItemInOffhand(controller, Items.SHIELD)) {
            controller.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
         }
      }
   }

   public boolean isTryingToEat() {
      return this.isTryingToEat;
   }

   @Override
   public float getPriority() {
      if (this.controller == null) {
         return Float.NEGATIVE_INFINITY;
      } else if (WorldHelper.isInNetherPortal(this.controller)) {
         this.stopEat(this.controller);
         return Float.NEGATIVE_INFINITY;
      } else if (this.controller.getMobDefenseChain().isShielding()) {
         this.stopEat(this.controller);
         return Float.NEGATIVE_INFINITY;
      } else {
         this.dragonBreathTracker.updateBreath(this.controller);

         for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(this.controller.getEntity())) {
            if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
               this.stopEat(this.controller);
               return Float.NEGATIVE_INFINITY;
            }
         }

         if (this.controller.getModSettings().isAutoEat() && !this.controller.getEntity().isInLava() && !this.shouldStop) {
            if (this.controller.getMLGBucketChain().doneMLG() && !this.controller.getMLGBucketChain().isFalling(this.controller)) {
               Tuple<Integer, Optional<Item>> calculation = this.calculateFood(this.controller);
               int foodScore = (Integer)calculation.getA();
               this.cachedPerfectFood = (Optional<Item>)calculation.getB();
               hasFood = foodScore > 0;
               if (this.requestFillup && this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel() >= 20) {
                  this.requestFillup = false;
               }

               if (!hasFood) {
                  this.requestFillup = false;
               }

               // Never eat while something is hunting it. Not a policy choice so much as an admission:
               // the defence chain re-equips a weapon every tick during a fight, so the food never
               // stays in hand long enough for a bite to finish, and trying anyway just thrashes the
               // hotbar between sword and snack. Being topped up beforehand is what actually helps,
               // which is what the new branch in needsToEat() is for.
               boolean threatened = !this.controller.getEntityTracker().getHostiles().isEmpty();
               if (!threatened && hasFood && (this.needsToEat() || this.requestFillup) && this.cachedPerfectFood.isPresent()) {
                  this.startEat(this.controller, this.cachedPerfectFood.get());
               } else {
                  this.stopEat(this.controller);
               }

               Settings settings = this.controller.getModSettings();
               if (this.needsToCollectFood || foodScore < settings.getMinimumFoodAllowed()) {
                  this.needsToCollectFood = foodScore < settings.getFoodUnitsToCollect();
                  if (this.needsToCollectFood) {
                     this.setTask(new CollectFoodTask(settings.getFoodUnitsToCollect()));
                     return 55.0F;
                  }
               }

               this.setTask(null);
               return Float.NEGATIVE_INFINITY;
            } else {
               this.stopEat(this.controller);
               return Float.NEGATIVE_INFINITY;
            }
         } else {
            this.stopEat(this.controller);
            return Float.NEGATIVE_INFINITY;
         }
      }
   }

   @Override
   public String getName() {
      return "Food chain";
   }

   @Override
   protected void onStop() {
      super.onStop();
      if (this.controller != null) {
         this.stopEat(this.controller);
      }
   }

   public boolean needsToEat() {
      if (hasFood && !this.shouldStop) {
         LivingEntity player = this.controller.getEntity();
         int foodLevel = this.controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
         float health = player.getHealth();
         if (foodLevel >= 20) {
            return false;
         } else if (health <= 10.0F) {
            return true;
         } else if (player.isOnFire() || player.hasEffect(MobEffects.WITHER) || health < config.alwaysEatWhenWitherOrFireAndHealthBelow) {
            return true;
         } else if (foodLevel <= config.alwaysEatWhenBelowHunger) {
            return true;
         } else if (health < config.alwaysEatWhenBelowHealth) {
            return true;
         } else if (foodLevel < config.alwaysEatWhenBelowHungerAndPerfectFit && this.cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = this.cachedPerfectFood.get();
            int fills = Optional.ofNullable(ItemVer.getFoodComponent(best)).map(FoodComponentWrapper::getHunger).orElse(-1);
            return fills > 0 && fills <= need;
         } else if (this.cachedPerfectFood.isPresent()) {
            // Top up whenever it is worth doing, rather than only once things are already bad.
            //
            // Every branch above triggers on being hurt or nearly starving — and those are exactly the
            // moments a companion cannot eat, because combat re-equips its weapon every tick and
            // interrupts the meal. A playtest caught the consequence exactly: it stood at full health
            // with 17/20 food and four rotten flesh in its pack for forty-nine seconds without eating,
            // then went into a fight, got to 8 health, finally qualified as "hungry enough", could not
            // eat because it was being shot at, and died. It could only ever try when it could not
            // succeed.
            //
            // A little waste is fine — a companion is not playing for food efficiency, and being full
            // before the fight starts is worth more than the last point of a rotten flesh.
            int need = 20 - foodLevel;
            Item best = this.cachedPerfectFood.get();
            int fills = Optional.ofNullable(ItemVer.getFoodComponent(best)).map(FoodComponentWrapper::getHunger).orElse(-1);
            return fills > 0 && fills - need <= 2;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private Tuple<Integer, Optional<Item>> calculateFood(AltoClefController controller) {
      Item bestFood = null;
      double bestFoodScore = Double.NEGATIVE_INFINITY;
      int foodTotal = 0;
      LivingEntity player = controller.getEntity();
      float health = player.getHealth();
      float hunger = controller.getBaritone().getEntityContext().hungerManager().getFoodLevel();
      float saturation = controller.getBaritone().getEntityContext().hungerManager().getSaturationLevel();

      for (ItemStack stack : controller.getItemStorage().getItemStacksPlayerInventory(true)) {
         if (ItemVer.isFood(stack) && !stack.is(Items.SPIDER_EYE)) {
            FoodComponentWrapper food = ItemVer.getFoodComponent(stack.getItem());
            if (food != null) {
               float hungerIfEaten = Math.min(hunger + food.getHunger(), 20.0F);
               float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.getSaturationModifier());
               float gainedSaturation = saturationIfEaten - saturation;
               float gainedHunger = hungerIfEaten - hunger;
               float hungerWasted = food.getHunger() - gainedHunger;
               float score = gainedSaturation * 2.0F - hungerWasted;
               // Rotten flesh used to be penalised by 100 — effectively "never, unless there is
               // literally nothing else" — because for a player it means a near-certain dose of
               // Hunger. A companion is a LivingEntity, and vanilla's Hunger effect is gated on
               // `instanceof PlayerEntity` before it adds any exhaustion, so the effect lands and then
               // does nothing at all. Rotten flesh is simply free food here, and it is the food a
               // companion actually finds: skeletons and zombies drop it constantly.
               //
               // Spider eyes stay excluded above, and that exclusion is still right — Poison damages
               // any LivingEntity, with no player check.

               if (score > bestFoodScore) {
                  bestFoodScore = score;
                  bestFood = stack.getItem();
               }

               foodTotal += food.getHunger() * stack.getCount();
            }
         }
      }

      return new Tuple(foodTotal, Optional.ofNullable(bestFood));
   }

   public boolean hasFood() {
      return hasFood;
   }

   public void shouldStop(boolean shouldStopInput) {
      this.shouldStop = shouldStopInput;
   }

   public boolean isShouldStop() {
      return this.shouldStop;
   }

   static {
      ConfigHelper.loadConfig(
         "configs/food_chain_settings.json", FoodChain.FoodChainConfig::new, FoodChain.FoodChainConfig.class, newConfig -> config = newConfig
      );
   }

   static class FoodChainConfig {
      public int alwaysEatWhenWitherOrFireAndHealthBelow = 6;
      public int alwaysEatWhenBelowHunger = 10;
      public int alwaysEatWhenBelowHealth = 14;
      public int alwaysEatWhenBelowHungerAndPerfectFit = 15;
   }
}
