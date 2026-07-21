package baritone.utils.schematic;

import baritone.PlayerEngine;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.MaskSchematic;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MapArtSchematic extends MaskSchematic {
   private final int[][] heightMap;

   public MapArtSchematic(IStaticSchematic schematic) {
      super(schematic);
      this.heightMap = generateHeightMap(schematic);
   }

   @Override
   protected boolean partOfMask(int x, int y, int z, BlockState currentState) {
      return y >= this.heightMap[x][z];
   }

   private static int[][] generateHeightMap(IStaticSchematic schematic) {
      int[][] heightMap = new int[schematic.widthX()][schematic.lengthZ()];

      for (int x = 0; x < schematic.widthX(); x++) {
         for (int z = 0; z < schematic.lengthZ(); z++) {
            BlockState[] column = schematic.getColumn(x, z);
            OptionalInt lowestBlockY = lastIndexMatching(column, state -> !(state.getBlock() instanceof AirBlock));
            if (lowestBlockY.isPresent()) {
               heightMap[x][z] = lowestBlockY.getAsInt();
            } else {
               PlayerEngine.LOGGER.warn("Column " + x + "," + z + " has no blocks, but it's apparently map art? wtf");
               PlayerEngine.LOGGER.warn("Letting it be whatever");
               heightMap[x][z] = 256;
            }
         }
      }

      return heightMap;
   }

   private static <T> OptionalInt lastIndexMatching(T[] arr, Predicate<? super T> predicate) {
      for (int y = arr.length - 1; y >= 0; y--) {
         if (predicate.test(arr[y])) {
            return OptionalInt.of(y);
         }
      }

      return OptionalInt.empty();
   }
}
