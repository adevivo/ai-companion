package baritone.utils.player;

import baritone.api.entity.IInteractionManagerProvider;
import baritone.api.entity.LivingEntityInteractionManager;
import baritone.api.utils.IInteractionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class EntityInteractionController implements IInteractionController {
   private final LivingEntity player;
   private int sequence;

   public EntityInteractionController(LivingEntity player) {
      this.player = player;
   }

   @Override
   public boolean hasBrokenBlock() {
      return this.getInteractionManager().hasBrokenBlock();
   }

   @Override
   public boolean onPlayerDamageBlock(BlockPos pos, Direction side) {
      LivingEntityInteractionManager interactionManager = this.getInteractionManager();
      if (interactionManager.isMining()) {
         int progress = interactionManager.getBlockBreakingProgress();
         if (progress >= 10) {
            this.getInteractionManager()
               .processBlockBreakingAction(
                  interactionManager.getMiningPos(), Action.STOP_DESTROY_BLOCK, side, this.player.level().getMaxBuildHeight(), this.sequence++
               );
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public void resetBlockRemoving() {
      LivingEntityInteractionManager interactionManager = this.getInteractionManager();
      if (interactionManager.isMining()) {
         this.getInteractionManager()
            .processBlockBreakingAction(
               interactionManager.getMiningPos(), Action.ABORT_DESTROY_BLOCK, Direction.UP, this.player.level().getMaxBuildHeight(), this.sequence++
            );
      }
   }

   @Override
   public GameType getGameType() {
      return GameType.SURVIVAL;
   }

   @Override
   public InteractionResult processRightClickBlock(LivingEntity player, Level world, InteractionHand hand, BlockHitResult result) {
      return this.getInteractionManager().interactBlock(this.player, this.player.level(), this.player.getItemInHand(hand), hand, result);
   }

   @Override
   public InteractionResult processRightClick(LivingEntity player, Level world, InteractionHand hand) {
      return this.getInteractionManager().interactItem(this.player, this.player.level(), this.player.getItemInHand(hand), hand);
   }

   @Override
   public boolean clickBlock(BlockPos loc, Direction face) {
      BlockState state = this.player.level().getBlockState(loc);
      if (state.isAir()) {
         return false;
      } else {
         this.getInteractionManager()
            .processBlockBreakingAction(loc, Action.START_DESTROY_BLOCK, face, this.player.level().getMaxBuildHeight(), this.sequence++);
         return this.getInteractionManager().isMining() || this.player.level().isEmptyBlock(loc);
      }
   }

   public LivingEntityInteractionManager getInteractionManager() {
      return this.player instanceof IInteractionManagerProvider managerProvider ? managerProvider.getInteractionManager() : null;
   }

   @Override
   public void setHittingBlock(boolean hittingBlock) {
   }

   @Override
   public double getBlockReachDistance() {
      return 4.5;
   }
}
