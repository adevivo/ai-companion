package baritone.utils.type;

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
