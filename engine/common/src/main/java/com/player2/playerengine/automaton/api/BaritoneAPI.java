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

package com.player2.playerengine.automaton.api;

import java.util.Calendar;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class BaritoneAPI {
   private static final IBaritoneProvider provider;

   public static IBaritoneProvider getProvider() {
      return provider;
   }

   public static Settings getGlobalSettings() {
      return getProvider().getGlobalSettings();
   }

   public static Component getPrefix() {
      Calendar now = Calendar.getInstance();
      boolean xd = now.get(2) == 3 && now.get(5) <= 3;
      MutableComponent baritone = Component.literal(xd ? "Automatoe" : (getGlobalSettings().shortBaritonePrefix.get() ? "A" : "Automatone"));
      baritone.setStyle(baritone.getStyle().applyFormat(ChatFormatting.GREEN));
      MutableComponent prefix = Component.literal("");
      prefix.setStyle(baritone.getStyle().applyFormat(ChatFormatting.DARK_GREEN));
      prefix.append("[");
      prefix.append(baritone);
      prefix.append("]");
      return prefix;
   }

   static {
      try {
         provider = (IBaritoneProvider)Class.forName("com.player2.playerengine.automaton.BaritoneProvider").getField("INSTANCE").get(null);
      } catch (ReflectiveOperationException var1) {
         throw new RuntimeException(var1);
      }
   }
}
