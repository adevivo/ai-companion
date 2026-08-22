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

package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeGoalXZ;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ExploreCommand extends Command {
   public ExploreCommand() {
      super("explore");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      if (args.hasAny()) {
         args.requireExactly(2);
      } else {
         args.requireMax(0);
      }

      BetterBlockPos feetPos = baritone.getEntityContext().feetPos();
      GoalXZ goal = args.hasAny() ? args.getDatatypePost(RelativeGoalXZ.INSTANCE, feetPos) : new GoalXZ(feetPos);
      baritone.getExploreProcess().explore(goal.getX(), goal.getZ());
      this.logDirect(source, String.format("Exploring from %s", goal.toString()));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return args.hasAtMost(2) ? args.tabCompleteDatatype(RelativeGoalXZ.INSTANCE) : Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Explore things";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Tell Automatone to explore randomly. If you used explorefilter before this, it will be applied.",
         "",
         "Usage:",
         "> explore - Explore from your current position.",
         "> explore <x> <z> - Explore from the specified X and Z position."
      );
   }
}
