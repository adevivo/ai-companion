package baritone.api.schematic;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

public abstract class MaskSchematic extends AbstractSchematic {
   private final ISchematic schematic;

   public MaskSchematic(ISchematic schematic) {
      super(schematic.widthX(), schematic.heightY(), schematic.lengthZ());
      this.schematic = schematic;
   }

   protected abstract boolean partOfMask(int var1, int var2, int var3, BlockState var4);

   @Override
   public boolean inSchematic(int x, int y, int z, BlockState currentState) {
      return this.schematic.inSchematic(x, y, z, currentState) && this.partOfMask(x, y, z, currentState);
   }

   @Override
   public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
      return this.schematic.desiredState(x, y, z, current, approxPlaceable);
   }
}
