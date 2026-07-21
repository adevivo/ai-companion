package adris.altoclef.mixins;

import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Connection.class})
public interface ClientConnectionAccessor {
   @Accessor("tickCount")
   int getTicks();
}
