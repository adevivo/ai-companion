package adris.altoclef.commandsystem;

import adris.altoclef.util.Dimension;
import java.util.ArrayList;
import java.util.List;

public class GotoTarget {
   private final int x;
   private final int y;
   private final int z;
   private final Dimension dimension;
   private final GotoTarget.GotoTargetCoordType type;

   public GotoTarget(int x, int y, int z, Dimension dimension, GotoTarget.GotoTargetCoordType type) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.dimension = dimension;
      this.type = type;
   }

   public static GotoTarget parseRemainder(String line) throws CommandException {
      line = line.trim();
      if (line.startsWith("(") && line.endsWith(")")) {
         line = line.substring(1, line.length() - 1);
      }
      // Models paste coordinates straight out of agentStatus/worldStatus, which render a Vec3 as
      // "(11.9026..., 70.0, -46.6656...)". Treat the separators as whitespace and accept decimals
      // below, so a copied position is a usable argument instead of 39 consecutive failures.
      line = line.replace(',', ' ').trim();

      String[] parts = line.split("\\s+");
      List<Integer> numbers = new ArrayList<>();
      Dimension dimension = null;

      for (String part : parts) {
         if (part.isEmpty()) {
            continue;
         }
         Integer num = parseCoordinate(part);
         if (num != null) {
            numbers.add(num);
         } else {
            dimension = (Dimension)Arg.parseEnum(part, Dimension.class);
            break;
         }
      }

      // Assigned before the constructor call, not inside it. These used to be written by a switch
      // expression passed as the fifth argument — but Java evaluates arguments left to right, so
      // x, y and z were read as 0 before the switch ever ran. Every `goto x y z` in the mod
      // travelled to world origin instead, with the correct coord type attached so the task looked
      // entirely legitimate. Observed as a companion walking off toward spawn from 220 blocks away.
      int x = 0;
      int y = 0;
      int z = 0;
      GotoTarget.GotoTargetCoordType type;
      switch (numbers.size()) {
         case 0 -> type = GotoTarget.GotoTargetCoordType.NONE;
         case 1 -> {
            y = numbers.get(0);
            type = GotoTarget.GotoTargetCoordType.Y;
         }
         case 2 -> {
            x = numbers.get(0);
            z = numbers.get(1);
            type = GotoTarget.GotoTargetCoordType.XZ;
         }
         case 3 -> {
            x = numbers.get(0);
            y = numbers.get(1);
            z = numbers.get(2);
            type = GotoTarget.GotoTargetCoordType.XYZ;
         }
         default -> throw new CommandException(
               "Unexpected number of integers passed to coordinate: " + numbers.size());
      }

      return new GotoTarget(x, y, z, dimension, type);
   }

   /**
    * A coordinate as a block index, or null when the token is not a number at all.
    *
    * <p>Decimals are floored rather than rejected: a position like {@code 70.0} or
    * {@code -46.66} names the block containing it, and flooring is what turns a negative
    * fractional coordinate into the right block rather than the one next door.
    */
   private static Integer parseCoordinate(String part) {
      try {
         return Integer.valueOf(Integer.parseInt(part));
      } catch (NumberFormatException notAnInt) {
         try {
            return Integer.valueOf((int) Math.floor(Double.parseDouble(part)));
         } catch (NumberFormatException notANumber) {
            return null;
         }
      }
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getZ() {
      return this.z;
   }

   public Dimension getDimension() {
      return this.dimension;
   }

   public boolean hasDimension() {
      return this.dimension != null;
   }

   public GotoTarget.GotoTargetCoordType getType() {
      return this.type;
   }

   public static enum GotoTargetCoordType {
      XYZ,
      XZ,
      Y,
      NONE;
   }
}
