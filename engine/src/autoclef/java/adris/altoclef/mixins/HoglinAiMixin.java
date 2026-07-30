package adris.altoclef.mixins;

import adris.altoclef.util.helpers.CompanionTargetHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Makes hoglins hostile to the companion, for the same reason as {@code PiglinAiMixin}: hoglins are
 * brain-driven and so are not covered by the goal-based widening.
 *
 * <p>No gold-armour exemption here — that truce is a piglin thing. Hoglins charge players on sight and
 * now do the same to the companion.
 */
@Mixin({HoglinAi.class})
public class HoglinAiMixin {
   @Inject(
      method = {"findNearestValidAttackTarget"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void aicompanion$alsoTargetCompanion(Hoglin hoglin, CallbackInfoReturnable<Optional<? extends LivingEntity>> cir) {
      LivingEntity companion = CompanionTargetHelper.nearestCompanion(hoglin);
      if (companion == null) {
         return;
      }

      Optional<? extends LivingEntity> current = cir.getReturnValue();
      if (current != null && current.isPresent()
         && hoglin.distanceToSqr(current.get()) <= hoglin.distanceToSqr(companion)) {
         return;
      }

      cir.setReturnValue(Optional.of(companion));
   }
}
