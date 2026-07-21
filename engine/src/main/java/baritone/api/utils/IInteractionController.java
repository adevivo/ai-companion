package baritone.api.utils;

import baritone.api.component.EntityComponentKey;
import baritone.utils.player.EntityInteractionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public interface IInteractionController {
   EntityComponentKey<IInteractionController> KEY = new EntityComponentKey<>(EntityInteractionController::new);

   boolean hasBrokenBlock();

   boolean onPlayerDamageBlock(BlockPos var1, Direction var2);

   void resetBlockRemoving();

   GameType getGameType();

   InteractionResult processRightClickBlock(LivingEntity var1, Level var2, InteractionHand var3, BlockHitResult var4);

   InteractionResult processRightClick(LivingEntity var1, Level var2, InteractionHand var3);

   boolean clickBlock(BlockPos var1, Direction var2);

   void setHittingBlock(boolean var1);

   double getBlockReachDistance();
}
