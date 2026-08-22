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
import com.player2.playerengine.automaton.api.command.datatypes.RelativeCoordinate;
import com.player2.playerengine.automaton.api.command.datatypes.RelativeGoal;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.process.ICustomGoalProcess;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class GoalCommand extends Command {
   public GoalCommand() {
      super("goal");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      ICustomGoalProcess goalProcess = baritone.getCustomGoalProcess();
      if (args.hasAny() && Arrays.asList("reset", "clear", "none").contains(args.peekString())) {
         args.requireMax(1);
         if (goalProcess.getGoal() != null) {
            goalProcess.setGoal(null);
            this.logDirect(source, "Cleared goal");
         } else {
            this.logDirect(source, "There was no goal to clear");
         }
      } else {
         args.requireMax(3);
         BetterBlockPos origin = baritone.getEntityContext().feetPos();
         Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
         goalProcess.setGoal(goal);
         this.logDirect(source, String.format("Goal: %s", goal.toString()));
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      TabCompleteHelper helper = new TabCompleteHelper();
      if (args.hasExactlyOne()) {
         helper.append("reset", "clear", "none", "~");
      } else if (args.hasAtMost(3)) {
         while (args.has(2) && args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            args.get();
            if (!args.has(2)) {
               helper.append("~");
            }
         }
      }

      return helper.filterPrefix(args.getString()).stream();
   }

   @Override
   public String getShortDesc() {
      return "Set or clear the goal";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The goal command allows you to set or clear Baritone's goal.",
         "",
         "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft commands. Or, you can just use regular numbers.",
         "",
         "Usage:",
         "> goal - Set the goal to your current position",
         "> goal <reset/clear/none> - Erase the goal",
         "> goal <y> - Set the goal to a Y level",
         "> goal <x> <z> - Set the goal to an X,Z position",
         "> goal <x> <y> <z> - Set the goal to an X,Y,Z position"
      );
   }
}
