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
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalInverted;
import com.player2.playerengine.automaton.api.process.ICustomGoalProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class InvertCommand extends Command {
   public InvertCommand() {
      super("invert");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
      Goal goal;
      if ((goal = customGoalProcess.getGoal()) == null) {
         throw new CommandInvalidStateException("No goal");
      } else {
         if (goal instanceof GoalInverted) {
            goal = ((GoalInverted)goal).origin;
         } else {
            goal = new GoalInverted(goal);
         }

         customGoalProcess.setGoalAndPath(goal);
         this.logDirect(source, String.format("Goal: %s", goal.toString()));
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Run away from the current goal";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The invert command tells Automatone to head away from the current goal rather than towards it.", "", "Usage:", "> invert - Invert the current goal."
      );
   }
}
