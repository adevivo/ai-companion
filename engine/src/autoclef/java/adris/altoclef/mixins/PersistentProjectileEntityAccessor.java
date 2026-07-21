package adris.altoclef.mixins;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractArrow.class})
public interface PersistentProjectileEntityAccessor {
   @Accessor("inGround")
   boolean isInGround();
}
