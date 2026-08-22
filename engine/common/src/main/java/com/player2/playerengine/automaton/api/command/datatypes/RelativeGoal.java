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

package com.player2.playerengine.automaton.api.command.datatypes;

import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.pathing.goals.GoalYLevel;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.stream.Stream;

public enum RelativeGoal implements IDatatypePost<Goal, BetterBlockPos> {
   INSTANCE;

   public Goal apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      GoalBlock goalBlock = consumer.peekDatatypePostOrNull(RelativeGoalBlock.INSTANCE, origin);
      if (goalBlock != null) {
         return goalBlock;
      } else {
         GoalXZ goalXZ = consumer.peekDatatypePostOrNull(RelativeGoalXZ.INSTANCE, origin);
         if (goalXZ != null) {
            return goalXZ;
         } else {
            GoalYLevel goalYLevel = consumer.peekDatatypePostOrNull(RelativeGoalYLevel.INSTANCE, origin);
            return (Goal)(goalYLevel != null ? goalYLevel : new GoalBlock(origin));
         }
      }
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      return ctx.getConsumer().tabCompleteDatatype(RelativeCoordinate.INSTANCE);
   }
}
