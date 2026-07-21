package adris.altoclef.trackers.blacklisting;

import adris.altoclef.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class WorldLocateBlacklist extends AbstractObjectBlacklist<BlockPos> {
   protected Vec3 getPos(BlockPos item) {
      return WorldHelper.toVec3d(item);
   }
}
