package adris.altoclef.mixins;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LivingEntity.class})
public interface LivingEntityMixin {
   @Accessor(value="attackStrengthTicker")
   int getLastAttackedTicks();
}
