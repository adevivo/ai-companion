package adris.altoclef.mixins.baritone;

import baritone.utils.accessor.ServerCommandSourceAccessor;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({CommandSourceStack.class})
public abstract class MixinServerCommandSource implements ServerCommandSourceAccessor {
   @Accessor("source")
   @Override
   public abstract CommandSource automatone$getOutput();
}
