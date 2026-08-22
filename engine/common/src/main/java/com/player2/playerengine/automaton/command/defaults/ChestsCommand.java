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
import com.player2.playerengine.automaton.api.cache.IRememberedInventory;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.command.exception.CommandInvalidStateException;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public class ChestsCommand extends Command {
   public ChestsCommand() {
      super("chests");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      Set<Entry<BlockPos, IRememberedInventory>> entries = baritone.getEntityContext().worldData().getContainerMemory().getRememberedInventories().entrySet();
      if (entries.isEmpty()) {
         throw new CommandInvalidStateException("No remembered inventories");
      } else {
         for (Entry<BlockPos, IRememberedInventory> entry : entries) {
            BetterBlockPos pos = new BetterBlockPos(entry.getKey());
            IRememberedInventory inv = entry.getValue();
            this.logDirect(source, pos.toString());

            for (ItemStack item : inv.getContents()) {
               MutableComponent component = (MutableComponent)item.getHoverName();
               component.append(String.format(" x %d", item.getCount()));
               this.logDirect(source, new Component[]{component});
            }
         }
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Display remembered inventories";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList("The chests command lists remembered inventories, I guess?", "", "Usage:", "> chests");
   }
}
