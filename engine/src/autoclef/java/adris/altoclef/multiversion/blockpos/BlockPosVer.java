package adris.altoclef.multiversion.blockpos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;

public class BlockPosVer {
   public static BlockPos ofFloored(Position pos) {
      return new BlockPos(Mth.floor(pos.x()), Mth.floor(pos.y()), Mth.floor(pos.z()));
   }

   public static double getSquaredDistance(BlockPos pos, Position obj) {
      return pos.distToCenterSqr(obj);
   }
}
