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

package com.player2.playerengine.automaton.utils;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.process.IBaritoneProcess;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public abstract class BaritoneProcessHelper implements IBaritoneProcess {
   protected final Baritone baritone;
   protected final IEntityContext ctx;

   public BaritoneProcessHelper(Baritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
   }

   @Override
   public boolean isTemporary() {
      return false;
   }

   public void logDirect(Component... components) {
      this.baritone.logDirect(components);
   }

   public void logDirect(String message, ChatFormatting color) {
      this.baritone.logDirect(message, color);
   }

   public void logDirect(String message) {
      this.baritone.logDirect(message);
   }
}
