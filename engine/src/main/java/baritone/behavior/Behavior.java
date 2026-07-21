package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.IBehavior;
import baritone.api.utils.IEntityContext;

public class Behavior implements IBehavior {
   public final Baritone baritone;
   public final IEntityContext ctx;

   protected Behavior(Baritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
      baritone.registerBehavior(this);
   }
}
