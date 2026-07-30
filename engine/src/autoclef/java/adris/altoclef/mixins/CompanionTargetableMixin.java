package adris.altoclef.mixins;

import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.util.helpers.CompanionTargetHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes goal-based hostiles hunt the companion as well as players.
 *
 * <p>One touchpoint covers most of the game: zombies, husks, drowned, skeletons, strays, spiders, cave
 * spiders, creepers, blazes, ghasts, slimes, magma cubes, silverfish, guardians, every illager, ravagers,
 * witches and vexes all target players through this goal.
 *
 * <p>Deliberately narrow in two ways. First, it only fires when the goal's own filter is a player type —
 * goals filtered on something else (a zombie's separate villager, iron-golem and turtle goals) are left
 * exactly as they were, so this widens player-hunting rather than making the companion universally
 * attackable. Second, it defers to the goal's own {@link TargetingConditions}, so line of sight,
 * invisibility and per-mob predicates apply to the companion the same way they apply to a person.
 *
 * <p>Injecting at TAIL rather than replacing the method means vanilla picks its player target first and
 * this only overrides when a companion is strictly closer — a mob standing between the owner and the
 * companion still goes for whoever is nearer, which is what a player would expect to see.
 *
 * <p>Mobs that never route through this method are untouched, and one of them matters for safety rather
 * than taste. {@code EnderMan.EndermanLookForPlayerGoal.tick()} casts its target to {@code Player} with
 * no instanceof guard, so feeding it a companion would be a ClassCastException inside a mob tick — a
 * world crash. It is safe only because that goal implements its own {@code canUse()} using
 * {@code getNearestPlayer} and never calls {@code findTarget()}, so this injection cannot reach it, and
 * because the field it casts is goal-local rather than the mob's target. Endermen therefore keep
 * stare-based player-only aggro and will only fight the companion in retaliation.
 *
 * <p>Every other cast of a mob target to {@code Player} in the entity packages was checked at the
 * bytecode level and is instanceof-guarded ({@code MeleeAttackGoal}, {@code PhantomSweepAttackGoal},
 * {@code EndermanFreezeWhenLookedAt}). Re-run that audit if this mixin is ever widened to fire for
 * goals whose {@code targetType} is not a player type.
 */
@Mixin({NearestAttackableTargetGoal.class})
public abstract class CompanionTargetableMixin {
   @Shadow
   @Final
   protected Class<? extends LivingEntity> targetType;

   @Shadow
   protected LivingEntity target;

   @Shadow
   protected TargetingConditions targetConditions;

   @Shadow
   protected abstract AABB getTargetSearchArea(double distance);

   @Inject(
      method = {"findTarget"},
      at = {@At("TAIL")}
   )
   private void aicompanion$alsoConsiderCompanion(CallbackInfo ci) {
      if (!BehaviorConfig.mobsTargetCompanion) {
         return;
      }

      // Only widen goals that were hunting players. Anything else keeps its own filter.
      if (this.targetType != Player.class && this.targetType != ServerPlayer.class) {
         return;
      }

      // `mob` is declared on TargetGoal, so it comes in through an accessor rather than an inherited
      // @Shadow the annotation processor cannot verify. Follow range is read the same way vanilla's
      // getFollowDistance() reads it, so the search box matches what a player-hunting scan would use.
      Mob mob = ((TargetGoalAccessor)this).getGoalMob();
      if (mob == null) {
         return;
      }

      AABB searchBox = this.getTargetSearchArea(CompanionTargetHelper.searchRange(mob));
      LivingEntity companion = CompanionTargetHelper.nearestCompanion(mob, searchBox, this.targetConditions);
      if (companion == null) {
         return;
      }

      // Whoever vanilla already picked wins ties and wins outright if they are closer.
      if (this.target != null && mob.distanceToSqr(this.target) <= mob.distanceToSqr(companion)) {
         return;
      }

      this.target = companion;
   }
}
