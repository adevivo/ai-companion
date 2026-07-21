package baritone.api.schematic;

import net.minecraft.world.level.block.state.BlockState;

public class WallsSchematic extends MaskSchematic {
   public WallsSchematic(ISchematic schematic) {
      super(schematic);
   }

   @Override
   protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
      return x == 0 || z == 0 || x == this.widthX() - 1 || z == this.lengthZ() - 1;
   }
}
