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

package com.player2.playerengine.automaton.api.command.manager;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.ICommand;
import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.registry.Registry;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Tuple;

public interface ICommandManager {
   Registry<ICommand> registry = new Registry<>();

   static ICommand getCommand(String name) {
      for (ICommand command : registry.entries) {
         if (command.getNames().contains(name.toLowerCase(Locale.ROOT))) {
            return command;
         }
      }

      return null;
   }

   IBaritone getBaritone();

   Registry<ICommand> getRegistry();

   boolean execute(CommandSourceStack var1, String var2) throws CommandException;

   boolean execute(CommandSourceStack var1, Tuple<String, List<ICommandArgument>> var2) throws CommandException;

   Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> var1);

   Stream<String> tabComplete(String var1);
}
