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
import com.player2.playerengine.automaton.api.command.datatypes.RelativeFile;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidTypeException;
import com.player2.playerengine.automaton.utils.DirUtil;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ExploreFilterCommand extends Command {
   public ExploreFilterCommand() {
      super("explorefilter");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(2);
      File file = args.getDatatypePost(RelativeFile.INSTANCE, DirUtil.getGameDir().toAbsolutePath().getParent().toFile());
      boolean invert = false;
      if (args.hasAny()) {
         if (!args.getString().equalsIgnoreCase("invert")) {
            throw new CommandInvalidTypeException(args.consumed(), "either \"invert\" or nothing");
         }

         invert = true;
      }

      try {
         baritone.getExploreProcess().applyJsonFilter(file.toPath().toAbsolutePath(), invert);
      } catch (NoSuchFileException var8) {
         throw new CommandInvalidStateException("File not found");
      } catch (JsonSyntaxException var9) {
         throw new CommandInvalidStateException("Invalid JSON syntax");
      } catch (Exception var10) {
         throw new IllegalStateException(var10);
      }

      this.logDirect(source, String.format("Explore filter applied. Inverted: %s", invert));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
      return args.hasExactlyOne() ? RelativeFile.tabComplete(args, RelativeFile.gameDir()) : Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Explore chunks from a json";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Apply an explore filter before using explore, which tells the explore process which chunks have been explored/not explored.",
         "",
         "The JSON file will follow this format: [{\"x\":0,\"z\":0},...]",
         "",
         "If 'invert' is specified, the chunks listed will be considered NOT explored, rather than explored.",
         "",
         "Usage:",
         "> explorefilter <path> [invert] - Load the JSON file referenced by the specified path. If invert is specified, it must be the literal word 'invert'."
      );
   }
}
