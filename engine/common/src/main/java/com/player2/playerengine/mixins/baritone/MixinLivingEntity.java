package com.player2.playerengine.mixins.baritone;

import com.player2.playerengine.automaton.utils.accessor.ILivingEntityAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LivingEntity.class})
public abstract class MixinLivingEntity implements ILivingEntityAccessor {
   @Invoker("decreaseAirSupply")
   @Override
   public abstract int automatone$getNextAirUnderwater(int var1);

   @Invoker("increaseAirSupply")
   @Override
   public abstract int automatone$getNextAirOnLand(int var1);
}
