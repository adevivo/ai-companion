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

package com.player2.playerengine.automaton.api.utils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public class BlockUtils {
   private static transient Map<String, Block> resourceCache = new HashMap<>();

   public static String blockToString(Block block) {
      Identifier loc = BuiltInRegistries.BLOCK.getKey(block);
      String name = loc.getPath();
      if (!loc.getNamespace().equals("minecraft")) {
         name = loc.toString();
      }

      return name;
   }

   public static Block stringToBlockRequired(String name) {
      Block block = stringToBlockNullable(name);
      if (block == null) {
         throw new IllegalArgumentException(String.format("Invalid block name %s", name));
      } else {
         return block;
      }
   }

   public static Block stringToBlockNullable(String name) {
      Block block = resourceCache.get(name);
      if (block != null) {
         return block;
      } else if (resourceCache.containsKey(name)) {
         return null;
      } else {
         block = (Block)BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name)).orElse(null);
         Map<String, Block> copy = new HashMap<>(resourceCache);
         copy.put(name, block);
         resourceCache = copy;
         return block;
      }
   }

   private BlockUtils() {
   }
}
