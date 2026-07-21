package adris.altoclef.eventbus.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class BlockPlaceEvent {
   public BlockPos blockPos;
   public BlockState blockState;

   public BlockPlaceEvent(BlockPos blockPos, BlockState blockState) {
      this.blockPos = blockPos;
      this.blockState = blockState;
   }
}
