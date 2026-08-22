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

package com.player2.playerengine.automaton.event;

import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.event.events.BlockInteractEvent;
import com.player2.playerengine.automaton.api.event.events.PathEvent;
import com.player2.playerengine.automaton.api.event.listener.IEventBus;
import com.player2.playerengine.automaton.api.event.listener.IGameEventListener;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameEventHandler implements IEventBus {
   private final Baritone baritone;
   private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

   public GameEventHandler(Baritone baritone) {
      this.baritone = baritone;
   }

   @Override
   public void onTickServer() {
      try {
         this.baritone.bsi = new BlockStateInterface(this.baritone.getEntityContext());
      } catch (Exception var2) {
         PlayerEngine.LOGGER.error(var2);
         this.baritone.bsi = null;
      }

      this.listeners.forEach(IGameEventListener::onTickServer);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      this.listeners.forEach(l -> l.onBlockInteract(event));
   }

   @Override
   public void onPathEvent(PathEvent event) {
      this.listeners.forEach(l -> l.onPathEvent(event));
   }

   @Override
   public final void registerEventListener(IGameEventListener listener) {
      this.listeners.add(listener);
   }
}
