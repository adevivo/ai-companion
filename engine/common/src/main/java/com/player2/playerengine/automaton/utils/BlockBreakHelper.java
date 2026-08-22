/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.api.utils.IEntityContext;
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
