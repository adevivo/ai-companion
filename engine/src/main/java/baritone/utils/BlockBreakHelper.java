package baritone.utils;

import baritone.api.utils.IEntityContext;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public final class BlockBreakHelper {
   private final IEntityContext ctx;
   @Nullable
   private BlockPos lastPos;

   BlockBreakHelper(IEntityContext ctx) {
      this.ctx = ctx;
   }

   public void stopBreakingBlock() {
      if (this.ctx.entity() != null && this.lastPos != null) {
         if (!this.ctx.playerController().hasBrokenBlock()) {
            this.ctx.playerController().setHittingBlock(true);
         }

         this.ctx.playerController().resetBlockRemoving();
         this.lastPos = null;
      }
   }

   public void tick(boolean isLeftClick) {
      HitResult trace = this.ctx.objectMouseOver();
      boolean isBlockTrace = trace != null && trace.getType() == Type.BLOCK;
      if (isLeftClick && isBlockTrace) {
         BlockPos pos = ((BlockHitResult)trace).getBlockPos();
         if (!Objects.equals(this.lastPos, pos)) {
            this.ctx.playerController().clickBlock(pos, ((BlockHitResult)trace).getDirection());
            this.ctx.entity().swing(InteractionHand.MAIN_HAND);
         }

         if (this.ctx.playerController().onPlayerDamageBlock(pos, ((BlockHitResult)trace).getDirection())) {
            this.ctx.entity().swing(InteractionHand.MAIN_HAND);
         }

         this.ctx.playerController().setHittingBlock(false);
         this.lastPos = pos;
      } else if (this.lastPos != null) {
         this.stopBreakingBlock();
         this.lastPos = null;
      }
   }
}
