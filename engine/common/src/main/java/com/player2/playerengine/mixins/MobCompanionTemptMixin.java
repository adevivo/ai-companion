package com.player2.playerengine.mixins;

import com.player2.playerengine.util.helpers.CompanionTemptGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every animal that a player can tempt a {@link CompanionTemptGoal} beside its TemptGoal, at the
 * same priority, so a companion holding wheat leads cows the way a player does.
 *
 * <p>At the TAIL of {@code Mob}'s constructor because that is where {@code registerGoals()} has just
 * run, server-side only, for every subclass. Copying the existing goal's presence and priority, rather
 * than listing animals, keeps this exactly as wide as vanilla tempting.
 */
@Mixin(Mob.class)
public abstract class MobCompanionTemptMixin {
   @Shadow
   @Final
   protected GoalSelector goalSelector;

   @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
   private void playerengine$addCompanionTempt(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
      Object self = this;
      if (level == null || level.isClientSide() || !(self instanceof Animal animal) || self instanceof Enemy) {
         return;
      }
      // Find first, add after: adding to the goal set while iterating it would be a CME.
      Integer priority = null;
      for (WrappedGoal wrapped : this.goalSelector.getAvailableGoals()) {
         if (wrapped.getGoal() instanceof TemptGoal) {
            priority = wrapped.getPriority();
            break;
         }
      }
      if (priority != null) {
         this.goalSelector.addGoal(priority, new CompanionTemptGoal(animal, 1.2));
      }
   }
}
