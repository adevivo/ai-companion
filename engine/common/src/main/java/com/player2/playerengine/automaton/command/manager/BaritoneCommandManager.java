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

package com.player2.playerengine.automaton.command.manager;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.ICommand;
import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.command.manager.ICommandManager;
import com.player2.playerengine.automaton.api.command.registry.Registry;
import com.player2.playerengine.automaton.command.CommandUnhandledException;
import com.player2.playerengine.automaton.command.argument.ArgConsumer;
import com.player2.playerengine.automaton.command.argument.CommandArguments;
import com.player2.playerengine.automaton.command.defaults.DefaultCommands;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Tuple;

public class BaritoneCommandManager implements ICommandManager {
   private final IBaritone baritone;

   public BaritoneCommandManager(Baritone baritone) {
      this.baritone = baritone;
   }

   @Override
   public IBaritone getBaritone() {
      return this.baritone;
   }

   @Override
   public Registry<ICommand> getRegistry() {
      return ICommandManager.registry;
   }

   @Override
   public boolean execute(CommandSourceStack source, String string) throws CommandException {
      return this.execute(source, expand(string));
   }

   @Override
   public boolean execute(CommandSourceStack source, Tuple<String, List<ICommandArgument>> expanded) throws CommandException {
      BaritoneCommandManager.ExecutionWrapper execution = this.from(expanded);
      if (execution != null) {
         execution.execute(source);
      }

      return execution != null;
   }

   @Override
   public Stream<String> tabComplete(Tuple<String, List<ICommandArgument>> expanded) {
      BaritoneCommandManager.ExecutionWrapper execution = this.from(expanded);
      return execution == null ? Stream.empty() : execution.tabComplete();
   }

   @Override
   public Stream<String> tabComplete(String prefix) {
      Tuple<String, List<ICommandArgument>> pair = expand(prefix, true);
      String label = (String)pair.getA();
      List<ICommandArgument> args = (List<ICommandArgument>)pair.getB();
      return args.isEmpty() ? new TabCompleteHelper().addCommands().filterPrefix(label).stream() : this.tabComplete(pair);
   }

   private BaritoneCommandManager.ExecutionWrapper from(Tuple<String, List<ICommandArgument>> expanded) {
      String label = (String)expanded.getA();
      ArgConsumer args = new ArgConsumer(this, (List<ICommandArgument>)expanded.getB(), this.getBaritone());
      ICommand command = ICommandManager.getCommand(label);
      return command == null ? null : new BaritoneCommandManager.ExecutionWrapper(this.baritone, command, label, args);
   }

   private static Tuple<String, List<ICommandArgument>> expand(String string, boolean preserveEmptyLast) {
      String label = string.split("\\s", 2)[0];
      List<ICommandArgument> args = CommandArguments.from(string.substring(label.length()), preserveEmptyLast);
      return new Tuple(label, args);
   }

   public static Tuple<String, List<ICommandArgument>> expand(String string) {
      return expand(string, false);
   }

   static {
      DefaultCommands.controlCommands.registerCommands();
   }

   private static final class ExecutionWrapper {
      private final IBaritone baritone;
      private final ICommand command;
      private final String label;
      private final ArgConsumer args;

      private ExecutionWrapper(IBaritone baritone, ICommand command, String label, ArgConsumer args) {
         this.baritone = baritone;
         this.command = command;
         this.label = label;
         this.args = args;
      }

      private void execute(CommandSourceStack source) throws CommandException {
         try {
            this.command.execute(source, this.label, this.args, this.baritone);
         } catch (Throwable var3) {
            throw var3 instanceof CommandException
               ? (CommandException)var3
               : new CommandUnhandledException(
                  "An unhandled exception occurred. The error is in your game's log, please report this at https://github.com/Ladysnake/Automatone/issues",
                  var3
               );
         }
      }

      private Stream<String> tabComplete() {
         try {
            return this.command.tabComplete(this.label, this.args).map(s -> {
               Deque<ICommandArgument> confirmedArgs = new ArrayDeque<>(this.args.getConsumed());
               confirmedArgs.removeLast();
               return Stream.concat(Stream.of(this.label), confirmedArgs.stream().map(ICommandArgument::getValue)).collect(Collectors.joining(" ")) + " " + s;
            });
         } catch (Throwable var2) {
            return Stream.empty();
         }
      }
   }
}
