package baritone.api.utils;

import baritone.api.behavior.IBehavior;
import baritone.api.utils.input.Input;

public interface IInputOverrideHandler extends IBehavior {
   boolean isInputForcedDown(Input var1);

   void setInputForceState(Input var1, boolean var2);

   void clearAllKeys();
}
