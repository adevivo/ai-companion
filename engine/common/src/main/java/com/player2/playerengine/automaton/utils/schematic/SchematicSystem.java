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

package com.player2.playerengine.automaton.utils.schematic;

import com.player2.playerengine.automaton.api.schematic.ISchematicSystem;
import com.player2.playerengine.automaton.api.schematic.format.ISchematicFormat;
import com.player2.playerengine.automaton.utils.schematic.format.DefaultSchematicFormats;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public enum SchematicSystem implements ISchematicSystem {
   INSTANCE;

   private final List<ISchematicFormat> registry = new ArrayList<>();

   private SchematicSystem() {
      Collections.addAll(this.registry, DefaultSchematicFormats.values());
   }

   @Override
   public List<ISchematicFormat> getRegistry() {
      return this.registry;
   }

   @Override
   public Optional<ISchematicFormat> getByFile(File file) {
      return this.registry.stream().filter(format -> format.isFileType(file)).findFirst();
   }
}
