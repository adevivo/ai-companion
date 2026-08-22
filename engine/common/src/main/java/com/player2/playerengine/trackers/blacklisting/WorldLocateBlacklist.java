package com.player2.playerengine.trackers.blacklisting;

import com.player2.playerengine.util.helpers.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class WorldLocateBlacklist extends AbstractObjectBlacklist<BlockPos> {
   protected Vec3 getPos(BlockPos item) {
      return WorldHelper.toVec3d(item);
   }
}
