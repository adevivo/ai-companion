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

package com.player2.playerengine.automaton.api.command;

import com.player2.playerengine.automaton.api.BaritoneAPI;
import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public interface ICommand {
   void execute(CommandSourceStack var1, String var2, IArgConsumer var3, IBaritone var4) throws CommandException;

   Stream<String> tabComplete(String var1, IArgConsumer var2) throws CommandException;

   String getShortDesc();

   List<String> getLongDesc();

   List<String> getNames();

   default boolean hiddenFromHelp() {
      return false;
   }

   default void logDirect(CommandSourceStack source, Component... components) {
      source.sendSuccess(() -> {
         MutableComponent component = Component.literal("");
         component.append(BaritoneAPI.getPrefix());
         component.append(Component.literal(" "));

         for (Component t : components) {
            component.append(t);
         }

         return component;
      }, false);
   }

   default void logDirect(CommandSourceStack source, String message, ChatFormatting color) {
      Stream.of(message.split("\n")).forEach(line -> {
         MutableComponent component = Component.literal(line.replace("\t", "    "));
         component.setStyle(component.getStyle().applyFormat(color));
         this.logDirect(source, component);
      });
   }

   default void logDirect(CommandSourceStack source, String message) {
      this.logDirect(source, message, ChatFormatting.GRAY);
   }
}
