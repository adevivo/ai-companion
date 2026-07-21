package baritone.api.schematic;

import java.util.List;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public interface ISchematic {
   default boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return x >= 0 && x < this.widthX() && y >= 0 && y < this.heightY() && z >= 0 && z < this.lengthZ();
   }

   default int size(Axis axis) {
      switch (axis) {
         case X:
            return this.widthX();
         case Y:
            return this.heightY();
         case Z:
            return this.lengthZ();
         default:
            throw new UnsupportedOperationException(axis + "");
      }
   }

   BlockState desiredState(int var1, int var2, int var3, BlockState var4, List<BlockState> var5);

   default void reset() {
   }

   int widthX();

   int heightY();

   int lengthZ();
}
