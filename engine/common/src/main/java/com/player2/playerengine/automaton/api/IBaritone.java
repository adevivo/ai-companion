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

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.behavior.ILookBehavior;
import com.player2.playerengine.automaton.api.behavior.IPathingBehavior;
import com.player2.playerengine.automaton.api.cache.IWorldProvider;
import com.player2.playerengine.automaton.api.command.manager.ICommandManager;
import com.player2.playerengine.automaton.api.component.EntityComponentKey;
import com.player2.playerengine.automaton.api.event.listener.IEventBus;
import com.player2.playerengine.automaton.api.pathing.calc.IPathingControlManager;
import com.player2.playerengine.automaton.api.process.IBuilderProcess;
import com.player2.playerengine.automaton.api.process.ICustomGoalProcess;
import com.player2.playerengine.automaton.api.process.IExploreProcess;
import com.player2.playerengine.automaton.api.process.IFarmProcess;
import com.player2.playerengine.automaton.api.process.IFollowProcess;
import com.player2.playerengine.automaton.api.process.IGetToBlockProcess;
import com.player2.playerengine.automaton.api.process.IMineProcess;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import com.player2.playerengine.automaton.api.utils.IInputOverrideHandler;
import java.util.Arrays;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public interface IBaritone {
   EntityComponentKey<IBaritone> KEY = new EntityComponentKey<>(Baritone::new);

   IPathingBehavior getPathingBehavior();

   ILookBehavior getLookBehavior();

   IFollowProcess getFollowProcess();

   IMineProcess getMineProcess();

   IBuilderProcess getBuilderProcess();

   IExploreProcess getExploreProcess();

   IFarmProcess getFarmProcess();

   ICustomGoalProcess getCustomGoalProcess();

   IGetToBlockProcess getGetToBlockProcess();

   IWorldProvider getWorldProvider();

   IPathingControlManager getPathingControlManager();

   IInputOverrideHandler getInputOverrideHandler();

   IEntityContext getEntityContext();

   IEventBus getGameEventHandler();

   ICommandManager getCommandManager();

   void logDebug(String var1);

   default void logDirect(Component... components) {
      IEntityContext playerContext = this.getEntityContext();
      LivingEntity entity = playerContext.entity();
      if (entity instanceof Player) {
         MutableComponent component = Component.literal("");
         component.append(BaritoneAPI.getPrefix());
         component.append(Component.literal(" "));
         Arrays.asList(components).forEach(component::append);
         ((Player)entity).displayClientMessage(component, false);
      } else {
         for (ServerPlayer p : entity.level().getServer().getPlayerList().getPlayers()) {
            if (p.isCreative()) {
               MutableComponent component = Component.literal("");
               component.append(BaritoneAPI.getPrefix());
               component.append(Component.literal(" "));
               Arrays.asList(components).forEach(component::append);
               p.displayClientMessage(component, false);
            }
         }
      }
   }

   default void logDirect(String message, ChatFormatting color) {
      Stream.of(message.split("\n")).forEach(line -> {
         MutableComponent component = Component.literal(line.replace("\t", "    "));
         component.setStyle(component.getStyle().applyFormat(color));
         this.logDirect(component);
      });
   }

   default void logDirect(String message) {
      this.logDirect(message, ChatFormatting.GRAY);
   }

   boolean isActive();

   Settings settings();

   void serverTick();
}
