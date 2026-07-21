package baritone.api.entity;

import baritone.api.IBaritone;

public interface IAutomatone {
   default IBaritone getBaritone() {
      return IBaritone.KEY.get(this);
   }
}
