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

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.utils.accessor.ServerCommandSourceAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

public class ClickCommand extends Command {
   public static final Identifier OPEN_CLICK_SCREEN = PlayerEngine.id("open_click_screen");

   public ClickCommand() {
      super("click");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);

      try {
         CommandSource t = ((ServerCommandSourceAccessor)source).automatone$getOutput();
      } catch (Throwable var6) {
         PlayerEngine.LOGGER.error("Failed to open click screen, is this a dedicated server?", var6);
      }

      this.logDirect(source, "aight dude");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Open click";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("Opens click dude", "", "Usage:", "> click");
   }
}
