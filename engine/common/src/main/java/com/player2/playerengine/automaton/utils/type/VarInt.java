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

package com.player2.playerengine.automaton.utils.type;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;

public final class VarInt {
   private final int value;
   private final byte[] serialized;
   private final int size;

   public VarInt(int value) {
      this.value = value;
      this.serialized = serialize0(this.value);
      this.size = this.serialized.length;
   }

   public final int getValue() {
      return this.value;
   }

   public final int getSize() {
      return this.size;
   }

   public final byte[] serialize() {
      return this.serialized;
   }

   private static byte[] serialize0(int valueIn) {
      ByteList bytes = new ByteArrayList();

      int value;
      for (value = valueIn; (value & 128) != 0; value >>>= 7) {
         bytes.add((byte)(value & 127 | 128));
      }

      bytes.add((byte)(value & 0xFF));
      return bytes.toByteArray();
   }

   public static VarInt read(byte[] bytes) {
      return read(bytes, 0);
   }

   public static VarInt read(byte[] bytes, int start) {
      int value = 0;
      int size = 0;
      int index = start;

      byte b;
      do {
         b = bytes[index++];
         value |= (b & 127) << size++ * 7;
         if (size > 5) {
            throw new IllegalArgumentException("VarInt size cannot exceed 5 bytes");
         }
      } while ((b & 128) != 0);

      return new VarInt(value);
   }
}
