package com.player2.playerengine.mixins;

import com.player2.playerengine.eventbus.EventBus;
import com.player2.playerengine.eventbus.events.BlockBreakingCancelEvent;
import com.player2.playerengine.eventbus.events.BlockBreakingEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MultiPlayerGameMode.class})
public final class ClientBlockBreakMixin {
   @Unique
   private static int breakCancelFrames;

   @Inject(
      method = {"continueDestroyBlock"},
      at = {@At("HEAD")}
   )
   private void onBreakUpdate(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> ci) {
      EventBus.publish(new BlockBreakingEvent(pos));
   }

   @Inject(
      method = {"stopDestroyBlock"},
      at = {@At("HEAD")}
   )
   private void cancelBlockBreaking(CallbackInfo ci) {
      if (breakCancelFrames-- == 0) {
         EventBus.publish(new BlockBreakingCancelEvent());
      }
   }
}
