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

package com.player2.playerengine.automaton;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.IBaritoneProvider;
import com.player2.playerengine.automaton.api.Settings;
import com.player2.playerengine.automaton.api.cache.IWorldScanner;
import com.player2.playerengine.automaton.api.command.ICommandSystem;
import com.player2.playerengine.automaton.api.schematic.ISchematicSystem;
import com.player2.playerengine.automaton.cache.WorldScanner;
import com.player2.playerengine.automaton.command.CommandSystem;
import com.player2.playerengine.automaton.utils.SettingsLoader;
import com.player2.playerengine.automaton.utils.schematic.SchematicSystem;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;

public final class BaritoneProvider implements IBaritoneProvider {
   public static final BaritoneProvider INSTANCE = new BaritoneProvider();
   private final Settings settings = new Settings();

   public BaritoneProvider() {
      SettingsLoader.readAndApply(this.settings);
   }

   @Override
   public IBaritone getBaritone(LivingEntity entity) {
      if (entity.level().isClientSide()) {
         throw new IllegalStateException("Lol we only support servers now");
      } else {
         return IBaritone.KEY.get(entity);
      }
   }

   public boolean isPathing(LivingEntity entity) {
      IBaritone baritone = IBaritone.KEY.getNullable(entity);
      return baritone != null && baritone.isActive();
   }

   @Override
   public IWorldScanner getWorldScanner() {
      return WorldScanner.INSTANCE;
   }

   @Override
   public ICommandSystem getCommandSystem() {
      return CommandSystem.INSTANCE;
   }

   @Override
   public ISchematicSystem getSchematicSystem() {
      return SchematicSystem.INSTANCE;
   }

   @Override
   public Settings getGlobalSettings() {
      return this.settings;
   }

   @Override
   public <E extends LivingEntity> Function<E, IBaritone> componentFactory() {
      return Baritone::new;
   }
}
