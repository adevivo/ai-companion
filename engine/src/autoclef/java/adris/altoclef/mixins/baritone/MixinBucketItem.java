package adris.altoclef.mixins.baritone;

import baritone.api.utils.IBucketAccessor;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({BucketItem.class})
public class MixinBucketItem implements IBucketAccessor {
   @Shadow
   @Final
   private Fluid content;

   @Override
   public Fluid getFluid() {
      return this.content;
   }
}
