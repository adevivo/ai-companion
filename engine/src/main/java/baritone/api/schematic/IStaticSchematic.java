package baritone.api.schematic;

import net.minecraft.world.level.block.state.BlockState;

public interface IStaticSchematic extends ISchematic {
   BlockState getDirect(int var1, int var2, int var3);

   default BlockState[] getColumn(int x, int z) {
      BlockState[] column = new BlockState[this.heightY()];

      for (int i = 0; i < this.heightY(); i++) {
         column[i] = this.getDirect(x, i, z);
      }

      return column;
   }
}
