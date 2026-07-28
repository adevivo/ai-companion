package baritone.api.utils;

import baritone.api.behavior.IBehavior;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface IInputOverrideHandler extends IBehavior {
   boolean isInputForcedDown(Input var1);

   void setInputForceState(Input var1, boolean var2);

   void clearAllKeys();

   /** The block this entity is currently mining, or {@code null} when it is not mining. */
   @Nullable
   BlockPos getBreakingBlockPos();
}
