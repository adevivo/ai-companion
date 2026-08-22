package com.player2.playerengine.mixins;

import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.EntitySwungEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class EntityAnimationSwungMixin {
   @Inject(
      method = {"swing(Lnet/minecraft/world/InteractionHand;)V"},
      at = {@At("HEAD")}
   )
   private void onEntityAnimation(InteractionHand hand, CallbackInfo ci) {
      EventBus.publish(new EntitySwungEvent((LivingEntity)(Object)this));
   }
}
