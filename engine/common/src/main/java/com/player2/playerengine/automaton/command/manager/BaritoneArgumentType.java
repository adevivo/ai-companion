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

import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import com.player2.playerengine.automaton.api.command.exception.CommandNotEnoughArgumentsException;
import com.player2.playerengine.automaton.api.command.helpers.TabCompleteHelper;
import com.player2.playerengine.automaton.api.command.manager.ICommandManager;
import com.player2.playerengine.automaton.command.argument.ArgConsumer;
import com.player2.playerengine.automaton.command.argument.CommandArguments;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BaritoneArgumentType implements ArgumentType<String> {
   public static BaritoneArgumentType baritone() {
      return new BaritoneArgumentType();
   }

   public static String getCommand(CommandContext<?> context, String name) {
      return (String)context.getArgument(name, String.class);
   }

   public String parse(StringReader reader) {
      String text = reader.getRemaining();
      reader.setCursor(reader.getTotalLength());
      return text;
   }

   public Stream<String> tabComplete(ICommandManager manager, String msg) {
      try {
         List<ICommandArgument> args = CommandArguments.from(msg, true);
         ArgConsumer argc = new ArgConsumer(manager, args, manager.getBaritone());
         return argc.hasAtMost(2) && argc.hasExactly(1)
            ? new TabCompleteHelper().addCommands().filterPrefix(argc.getString()).stream()
            : manager.tabComplete(msg);
      } catch (CommandNotEnoughArgumentsException var5) {
         return Stream.empty();
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return Suggestions.empty();
   }

   public Collection<String> getExamples() {
      return Arrays.asList("goto x y z", "click");
   }
}
