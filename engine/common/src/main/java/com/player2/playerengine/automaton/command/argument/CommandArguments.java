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

package com.player2.playerengine.automaton.command.argument;

import com.player2.playerengine.automaton.api.command.argument.ICommandArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandArguments {
   private static final Pattern ARG_PATTERN = Pattern.compile("\\S+");

   private CommandArguments() {
   }

   public static List<ICommandArgument> from(String string, boolean preserveEmptyLast) {
      List<ICommandArgument> args = new ArrayList<>();
      Matcher argMatcher = ARG_PATTERN.matcher(string);

      int lastEnd;
      for (lastEnd = -1; argMatcher.find(); lastEnd = argMatcher.end()) {
         args.add(new CommandArgument(args.size(), argMatcher.group(), string.substring(argMatcher.start())));
      }

      if (preserveEmptyLast && lastEnd < string.length()) {
         args.add(new CommandArgument(args.size(), "", ""));
      }

      return args;
   }

   public static List<ICommandArgument> from(String string) {
      return from(string, false);
   }

   public static CommandArgument unknown() {
      return new CommandArgument(-1, "<unknown>", "");
   }
}
