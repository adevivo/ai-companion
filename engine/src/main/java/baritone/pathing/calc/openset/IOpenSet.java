package baritone.pathing.calc.openset;

import baritone.pathing.calc.PathNode;

public interface IOpenSet {
   void insert(PathNode var1);

   boolean isEmpty();

   PathNode removeLowest();

   void update(PathNode var1);
}
