package baritone.api.behavior;

import baritone.api.utils.Rotation;

public interface ILookBehavior extends IBehavior {
   void updateTarget(Rotation var1, boolean var2);

   void updateSecondaryTarget(Rotation var1);
}
