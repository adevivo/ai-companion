package baritone.api.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

public class LivingEntityHungerManager {
   private int foodLevel = 20;
   private float foodSaturationLevel = 20.0F;
   private float exhaustion;
   private int foodTickTimer;
   private int prevFoodLevel = 20;

   public void add(int food, float saturationModifier) {
      this.foodLevel = Math.min(food + this.foodLevel, 20);
      this.foodSaturationLevel = Math.min(this.foodSaturationLevel + food * saturationModifier * 2.0F, (float)this.foodLevel);
   }

   public void eat(Item item, ItemStack stack) {
      if (item.isEdible()) {
         FoodProperties foodComponent = item.getFoodProperties();
         this.add(foodComponent.getNutrition(), foodComponent.getSaturationModifier());
      }
   }

   /**
    * Vanilla hunger and regeneration for a companion — everything {@link #update} does except starving
    * to death.
    *
    * <p>This used to skip {@link #addExhaustion} entirely, on the reasoning that exhaustion drains
    * saturation and then food, and a companion with no working way to eat would starve. That reasoning
    * was circular, and the omission was self-defeating: exhaustion is the <em>only</em> thing that
    * drains saturation, and {@link #update} is never called for a companion. So saturation and food
    * both sat pinned at 20 forever, the {@code saturation > 0 && foodLevel >= 20} branch was
    * permanently true, and the companion healed a flat 1.0 HP every 10 ticks — 2 HP a second,
    * near-death to full in ten seconds, indefinitely, at no cost. Vanilla reaches that rate only in
    * short bursts, because each heal spends 6.0 exhaustion.
    *
    * <p>It also froze the food system solid. Nothing could ever lower {@code foodLevel} below 20, so
    * {@code EatCommand} refused every time as "already at full food", {@code FoodChain} never
    * requested a fillup, and the {@code food} and {@code meat} commands reported a permanent 20/20.
    * Four commands that could not do anything become real once food can actually fall.
    *
    * <p><b>Starvation damage is deliberately not included.</b> A hungry companion stops regenerating
    * and waits to be fed; it does not die of neglect in a corner while its owner is offline. Healing
    * has a cost and running out of food has a consequence, without either being lethal on its own.
    *
    * <p>Hunger is intentionally not persisted — see {@code CompanionEntity}'s NBT methods, which do
    * not call {@link #readNbt}/{@link #writeNbt}. The cost that matters is the one inside a session.
    *
    * <p>Must be called every tick: the {@code foodTickTimer} thresholds below are tick counts.
    */
   public void tickCompanion(LivingEntity entity) {
      Difficulty difficulty = entity.level().getDifficulty();
      this.prevFoodLevel = this.foodLevel;
      // Exhaustion accrued by healing (and by anything else that calls addExhaustion) is converted here,
      // one step per tick, exactly as vanilla does it: 4.0 exhaustion costs a point of saturation, and
      // once saturation is gone it costs a point of food.
      if (this.exhaustion > 4.0F) {
         this.exhaustion -= 4.0F;
         if (this.foodSaturationLevel > 0.0F) {
            this.foodSaturationLevel = Math.max(this.foodSaturationLevel - 1.0F, 0.0F);
         } else if (difficulty != Difficulty.PEACEFUL) {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
         }
      }

      if (!entity.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION) || !this.canFoodHeal(entity)) {
         this.foodTickTimer = 0;
         return;
      }
      if (this.foodSaturationLevel > 0.0F && this.foodLevel >= 20) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 10) {
            float f = Math.min(this.foodSaturationLevel, 6.0F);
            entity.heal(f / 6.0F);
            this.addExhaustion(f);
            this.foodTickTimer = 0;
         }
      } else if (this.foodLevel >= 18) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 80) {
            entity.heal(1.0F);
            this.addExhaustion(6.0F);
            this.foodTickTimer = 0;
         }
      } else {
         // Below 18 food there is no natural regeneration, same as vanilla. The companion holds at
         // whatever health it has until somebody feeds it. It does not take starvation damage.
         this.foodTickTimer = 0;
      }
   }

   public void update(LivingEntity player) {
      Difficulty difficulty = player.level().getDifficulty();
      this.prevFoodLevel = this.foodLevel;
      if (this.exhaustion > 4.0F) {
         this.exhaustion -= 4.0F;
         if (this.foodSaturationLevel > 0.0F) {
            this.foodSaturationLevel = Math.max(this.foodSaturationLevel - 1.0F, 0.0F);
         } else if (difficulty != Difficulty.PEACEFUL) {
            this.foodLevel = Math.max(this.foodLevel - 1, 0);
         }
      }

      boolean bl = player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
      if (bl && this.foodSaturationLevel > 0.0F && this.canFoodHeal(player) && this.foodLevel >= 20) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 10) {
            float f = Math.min(this.foodSaturationLevel, 6.0F);
            player.heal(f / 6.0F);
            this.addExhaustion(f);
            this.foodTickTimer = 0;
         }
      } else if (bl && this.foodLevel >= 18 && this.canFoodHeal(player)) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 80) {
            player.heal(1.0F);
            this.addExhaustion(6.0F);
            this.foodTickTimer = 0;
         }
      } else if (this.foodLevel <= 0) {
         this.foodTickTimer++;
         if (this.foodTickTimer >= 80) {
            if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
               player.hurt(player.damageSources().starve(), 1.0F);
            }

            this.foodTickTimer = 0;
         }
      } else {
         this.foodTickTimer = 0;
      }
   }

   public void readNbt(CompoundTag nbt) {
      if (nbt.contains("foodLevel", 99)) {
         this.foodLevel = nbt.getInt("foodLevel");
         this.foodTickTimer = nbt.getInt("foodTickTimer");
         this.foodSaturationLevel = nbt.getFloat("foodSaturationLevel");
         this.exhaustion = nbt.getFloat("foodExhaustionLevel");
      }
   }

   public void writeNbt(CompoundTag nbt) {
      nbt.putInt("foodLevel", this.foodLevel);
      nbt.putInt("foodTickTimer", this.foodTickTimer);
      nbt.putFloat("foodSaturationLevel", this.foodSaturationLevel);
      nbt.putFloat("foodExhaustionLevel", this.exhaustion);
   }

   public int getFoodLevel() {
      return this.foodLevel;
   }

   public int getPrevFoodLevel() {
      return this.prevFoodLevel;
   }

   public boolean isNotFull() {
      return this.foodLevel < 20;
   }

   public void addExhaustion(float exhaustion) {
      this.exhaustion = Math.min(this.exhaustion + exhaustion, 40.0F);
   }

   public float getExhaustion() {
      return this.exhaustion;
   }

   public float getSaturationLevel() {
      return this.foodSaturationLevel;
   }

   public void setFoodLevel(int foodLevel) {
      this.foodLevel = foodLevel;
   }

   public void setSaturationLevel(float saturationLevel) {
      this.foodSaturationLevel = saturationLevel;
   }

   public void setExhaustion(float exhaustion) {
      this.exhaustion = exhaustion;
   }

   public boolean canFoodHeal(LivingEntity entity) {
      return entity.getHealth() > 0.0F && entity.getHealth() < entity.getMaxHealth();
   }
}
