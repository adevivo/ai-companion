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

package com.player2.playerengine.automaton.pathing.movement.movements;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.pathing.movement.MovementStatus;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.input.Input;
import com.player2.playerengine.automaton.pathing.movement.CalculationContext;
import com.player2.playerengine.automaton.pathing.movement.Movement;
import com.player2.playerengine.automaton.pathing.movement.MovementHelper;
import com.player2.playerengine.automaton.pathing.movement.MovementState;
import com.player2.playerengine.automaton.utils.pathing.MutableMoveResult;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MovementDownward extends Movement {
   private int numTicks = 0;

   public MovementDownward(IBaritone baritone, BetterBlockPos start, BetterBlockPos end) {
      super(baritone, start, end, buildPositionsToBreak(baritone.getEntityContext().entity(), end));
   }

   public static BetterBlockPos[] buildPositionsToBreak(Entity entity, BetterBlockPos end) {
      int x = end.x;
      int y = end.y;
      int z = end.z;
      EntityDimensions dims = entity.getDimensions(Pose.STANDING);
      int requiredSideSpace = CalculationContext.getRequiredSideSpace(dims);
      int sideLength = requiredSideSpace * 2 + 1;
      BetterBlockPos[] ret = new BetterBlockPos[sideLength * sideLength];
      int i = 0;

      for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
         for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
            ret[i++] = new BetterBlockPos(x + dx, y, z + dz);
         }
      }

      return ret;
   }

   @Override
   public void reset() {
      super.reset();
      this.numTicks = 0;
   }

   @Override
   public double calculateCost(CalculationContext context) {
      MutableMoveResult result = new MutableMoveResult();
      cost(context, this.src.x, this.src.y, this.src.z, result);
      return result.cost;
   }

   @Override
   protected Set<BetterBlockPos> calculateValidPositions() {
      return ImmutableSet.of(this.src, this.dest);
   }

   public static void cost(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
      if (context.allowDownward) {
         if (MovementHelper.canWalkOn(context.bsi, x, y - 2, z, context.baritone.settings())) {
            BlockState downBlock = context.get(x, y - 1, z);
            BlockState fromBlock = context.get(x, y, z);
            if (!fromBlock.is(Blocks.SCAFFOLDING) || !(Boolean)fromBlock.getValue(ScaffoldingBlock.BOTTOM)) {
               if (downBlock.is(BlockTags.CLIMBABLE)) {
                  if (fromBlock.is(BlockTags.CLIMBABLE) && downBlock.is(Blocks.SCAFFOLDING) && !fromBlock.is(Blocks.SCAFFOLDING)) {
                     return;
                  }

                  if (context.requiredSideSpace == 0) {
                     result.cost = 6.666666666666667;
                  }
               } else {
                  double totalHardness = 0.0;
                  int requiredSideSpace = context.requiredSideSpace;
                  boolean waterFloor = false;
                  BlockState headState = context.get(x, y + context.height - 1, z);
                  boolean inWater = MovementHelper.isWater(headState);

                  for (int dx = -requiredSideSpace; dx <= requiredSideSpace; dx++) {
                     for (int dz = -requiredSideSpace; dz <= requiredSideSpace; dz++) {
                        int checkedX = x + dx;
                        int checkedZ = z + dz;
                        BlockState toBreak = context.get(checkedX, y - 1, checkedZ);
                        totalHardness += MovementHelper.getMiningDurationTicks(context, checkedX, y - 1, checkedZ, toBreak, false);
                        if (MovementHelper.isWater(toBreak)) {
                           waterFloor = true;
                        }
                     }
                  }

                  if (inWater) {
                     totalHardness *= 5.0;
                  }

                  double fallCost = (waterFloor ? context.waterWalkSpeed / 4.63284688441047 : 1.0) * FALL_N_BLOCKS_COST[1];
                  result.cost = fallCost + totalHardness;
                  result.oxygenCost = context.oxygenCost(fallCost * 0.5 + totalHardness, headState) + context.oxygenCost(fallCost * 0.5, fromBlock);
               }
            }
         }
      }
   }

   @Override
   public MovementState updateState(MovementState state) {
      super.updateState(state);
      if (state.getStatus() != MovementStatus.RUNNING) {
         return state;
      } else if (this.ctx.feetPos().equals(this.dest)) {
         return state.setStatus(MovementStatus.SUCCESS);
      } else if (!this.playerInValidPosition()) {
         return state.setStatus(MovementStatus.UNREACHABLE);
      } else {
         double diffX = this.ctx.entity().getX() - (this.dest.getX() + 0.5);
         double diffZ = this.ctx.entity().getZ() - (this.dest.getZ() + 0.5);
         double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);
         if (this.numTicks++ < 10 && ab < 0.2) {
            if (((Baritone)this.baritone).bsi.get0(this.baritone.getEntityContext().feetPos().down()).is(Blocks.SCAFFOLDING)) {
               state.setInput(Input.SNEAK, true);
            } else if (this.ctx.entity().isUnderWater()) {
               state.setInput(Input.SNEAK, true);
            }

            return state;
         } else {
            MovementHelper.moveTowards(this.ctx, state, this.dest);
            return state;
         }
      }
   }
}
