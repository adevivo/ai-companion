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
import com.player2.playerengine.automaton.api.process.IGetToBlockProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class BlacklistCommand extends Command {
   public BlacklistCommand() {
      super("blacklist");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IGetToBlockProcess proc = baritone.getGetToBlockProcess();
      if (!proc.isActive()) {
         throw new CommandInvalidStateException("GetToBlockProcess is not currently active");
      } else if (proc.blacklistClosest()) {
         this.logDirect(source, "Blacklisted closest instances");
      } else {
         throw new CommandInvalidStateException("No known locations, unable to blacklist");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Blacklist closest block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "While going to a block this command blacklists the closest block so that block finding processes won't attempt to get to it.",
         "",
         "Usage:",
         "> blacklist"
      );
   }
}
