package baritone.utils;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class BlockStateInterfaceAccessWrapper implements BlockGetter {
   private final BlockStateInterface bsi;

   BlockStateInterfaceAccessWrapper(BlockStateInterface bsi) {
      this.bsi = bsi;
   }

   @Nullable
   public BlockEntity getBlockEntity(BlockPos pos) {
      return null;
   }

   public BlockState getBlockState(BlockPos pos) {
      return this.bsi.get0(pos.getX(), pos.getY(), pos.getZ());
   }

   public FluidState getFluidState(BlockPos blockPos) {
      return this.getBlockState(blockPos).getFluidState();
   }

   public int getHeight() {
      return this.bsi.world.getHeight();
   }

   public int getMinBuildHeight() {
      return this.bsi.world.getMinBuildHeight();
   }
}
