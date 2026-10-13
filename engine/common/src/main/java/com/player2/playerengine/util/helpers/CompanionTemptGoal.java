package com.player2.playerengine.util.helpers;

import com.player2.playerengine.automaton.api.entity.IAutomatone;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;

/**
 * Vanilla's {@code TemptGoal} for a companion instead of a player: an animal walks toward a nearby
 * companion holding its food, so she can lead cows, pigs, chickens and the rest the way a player
 * does. Vanilla's goal stores its target as a {@code Player}, so a companion could never tempt
 * anything, however much wheat she held.
 *
 * <p>Food is the animal's own {@link Animal#isFood}, so every animal follows what vanilla says it
 * eats with no list to maintain. Installed by {@code MobCompanionTemptMixin} only on animals that
 * already carry a player TemptGoal, at the same priority, so it never makes an animal temptable that
 * a player can't tempt, and brain-driven animals (goats, axolotls, frogs, camels…) are left alone
 * rather than given a second, competing source of movement.
 */
public class CompanionTemptGoal extends Goal {
   /** Vanilla's tempt range. */
   private static final double RANGE = 10.0;
   private static final double STOP_DISTANCE = 2.5;
   /** Searching every tick for every animal is wasteful; half a second still feels immediate. */
   private static final int SEARCH_INTERVAL = 10;

   private final Animal animal;
   private final double speed;
   private LivingEntity companion;
   private int searchCooldown;

   public CompanionTemptGoal(Animal animal, double speed) {
      this.animal = animal;
      this.speed = speed;
      this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
   }

   private boolean holdsFood(LivingEntity entity) {
      return this.animal.isFood(entity.getMainHandItem()) || this.animal.isFood(entity.getOffhandItem());
   }

   private boolean isTempting(LivingEntity entity) {
      return entity instanceof IAutomatone && entity.isAlive() && !entity.isSpectator()
            && entity.distanceToSqr(this.animal) <= RANGE * RANGE && this.holdsFood(entity);
   }

   @Override
   public boolean canUse() {
      if (this.searchCooldown > 0) {
         this.searchCooldown--;
         return false;
      }
      this.searchCooldown = SEARCH_INTERVAL;
      this.companion = this.animal.level()
            .getEntitiesOfClass(LivingEntity.class, this.animal.getBoundingBox().inflate(RANGE), this::isTempting)
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(this.animal)))
            .orElse(null);
      return this.companion != null;
   }

   @Override
   public boolean canContinueToUse() {
      return this.companion != null && this.isTempting(this.companion);
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   @Override
   public void tick() {
      this.animal.getLookControl().setLookAt(this.companion, this.animal.getMaxHeadYRot() + 20, this.animal.getMaxHeadXRot());
      if (this.animal.distanceToSqr(this.companion) < STOP_DISTANCE * STOP_DISTANCE) {
         this.animal.getNavigation().stop();
      } else {
         this.animal.getNavigation().moveTo(this.companion, this.speed);
      }
   }

   @Override
   public void stop() {
      this.companion = null;
      this.animal.getNavigation().stop();
   }
}
