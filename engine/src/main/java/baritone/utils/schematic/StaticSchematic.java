package baritone.utils.schematic;

import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.IStaticSchematic;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

public class StaticSchematic extends AbstractSchematic implements IStaticSchematic {
   protected BlockState[][][] states;

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      return this.states[x][z][y];
   }

   @Override
   public BlockState getDirect(int x, int y, int z) {
      return this.states[x][z][y];
   }

   @Override
   public BlockState[] getColumn(int x, int z) {
      return this.states[x][z];
   }
}
