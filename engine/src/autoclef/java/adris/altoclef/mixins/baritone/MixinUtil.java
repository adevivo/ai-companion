package adris.altoclef.mixins.baritone;

import baritone.PlayerEngine;
import java.util.concurrent.ExecutorService;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Util.class})
public abstract class MixinUtil {
   @Shadow
   private static void shutdownExecutor(ExecutorService service) {
   }

   @Inject(
      method = {"shutdownExecutors"},
      at = {@At("RETURN")}
   )
   private static void shutdownBaritoneExecutor(CallbackInfo ci) {
      shutdownExecutor(PlayerEngine.getExecutor());
   }
}
