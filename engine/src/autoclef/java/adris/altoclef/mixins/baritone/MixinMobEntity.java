package adris.altoclef.mixins.baritone;

import baritone.BaritoneProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class MixinMobEntity {
   @Shadow
   public abstract void setSpeed(float speed) ;

   @Inject(
      method = {"serverAiStep"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void cancelAiTick(CallbackInfo ci) {
      if (BaritoneProvider.INSTANCE.isPathing((LivingEntity)(Object)this)) {
         float forwardSpeed = ((LivingEntity)(Object)this).zza;
         this.setSpeed((float)((LivingEntity)(Object)this).getAttributeValue(Attributes.MOVEMENT_SPEED));
         ((LivingEntity)(Object)this).zza = forwardSpeed;
         ci.cancel();
      }
   }
}
