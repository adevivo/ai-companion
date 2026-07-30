package adris.altoclef.mixins;

import adris.altoclef.util.helpers.CompanionTargetHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Makes piglins hostile to the companion.
 *
 * <p>Piglins are brain-driven, so {@code CompanionTargetableMixin} does not reach them — they have no
 * {@code NearestAttackableTargetGoal}. This hooks their target chooser instead, which conveniently
 * returns {@code Optional<? extends LivingEntity>} and so accepts a non-player without any type
 * trickery. The sibling memory {@code NEAREST_VISIBLE_TARGETABLE_PLAYER} is typed to {@code Player} and
 * is deliberately left alone: writing a companion into it would hand a {@code ClassCastException} to
 * whichever piglin behaviour read it next.
 *
 * <p>The gold-armour truce is honoured, so a companion kitted out in gold walks through the Nether
 * unbothered exactly as a player would. That works because the companion's equipment lookups are backed
 * by its real inventory.
 */
@Mixin({PiglinAi.class})
public class PiglinAiMixin {
   @Inject(
      method = {"findNearestValidAttackTarget"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void aicompanion$alsoTargetCompanion(Piglin piglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
      LivingEntity companion = CompanionTargetHelper.nearestCompanion(piglin);
      if (companion == null || PiglinAi.isWearingGold(companion)) {
         return;
      }

      Optional<? extends LivingEntity> current = cir.getReturnValue();
      if (current != null && current.isPresent()
         && piglin.distanceToSqr(current.get()) <= piglin.distanceToSqr(companion)) {
         return;
      }

      cir.setReturnValue(Optional.of(companion));
   }
}
