package adris.altoclef.tasks.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public class PlaceStructureBlockTask extends PlaceBlockTask {
   public PlaceStructureBlockTask(BlockPos target) {
      super(target, new Block[0], true, true);
   }
}
