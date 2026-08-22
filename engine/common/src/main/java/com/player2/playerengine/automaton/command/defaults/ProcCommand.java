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
import com.player2.playerengine.automaton.api.pathing.calc.IPathingControlManager;
import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ProcCommand extends Command {
   public ProcCommand() {
      super("proc");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IPathingControlManager pathingControlManager = baritone.getPathingControlManager();
      IBaritoneProcess process = pathingControlManager.mostRecentInControl().orElse(null);
      if (process == null) {
         throw new CommandInvalidStateException("No process in control");
      } else {
         this.logDirect(
            source,
            String.format(
               "Class: %s\nPriority: %f\nTemporary: %b\nDisplay name: %s\nLast command: %s",
               process.getClass().getTypeName(),
               process.priority(),
               process.isTemporary(),
               process.displayName(),
               pathingControlManager.mostRecentCommand().map(PathingCommand::toString).orElse("None")
            )
         );
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "View process state information";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The proc command provides miscellaneous information about the process currently controlling an entity.",
         "",
         "You are not expected to understand this if you aren't familiar with implementation details.",
         "",
         "Usage:",
         "> proc - View process information, if present"
      );
   }
}
