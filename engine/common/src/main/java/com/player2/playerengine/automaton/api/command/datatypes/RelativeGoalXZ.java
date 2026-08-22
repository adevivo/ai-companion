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
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.stream.Stream;
import net.minecraft.util.Mth;

public enum RelativeGoalXZ implements IDatatypePost<GoalXZ, BetterBlockPos> {
   INSTANCE;

   public GoalXZ apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
      if (origin == null) {
         origin = BetterBlockPos.ORIGIN;
      }

      IArgConsumer consumer = ctx.getConsumer();
      return new GoalXZ(
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.x)),
         Mth.floor(consumer.getDatatypePost(RelativeCoordinate.INSTANCE, (double)origin.z))
      );
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) {
      IArgConsumer consumer = ctx.getConsumer();
      return consumer.hasAtMost(2) ? consumer.tabCompleteDatatype(RelativeCoordinate.INSTANCE) : Stream.empty();
   }
}
