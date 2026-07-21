package baritone.utils.schematic.format.defaults;

import baritone.utils.schematic.StaticSchematic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.ItemIdFix;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class MCEditSchematic extends StaticSchematic {
   public MCEditSchematic(CompoundTag schematic) {
      String type = schematic.getString("Materials");
      if (!type.equals("Alpha")) {
         throw new IllegalStateException("bad schematic " + type);
      } else {
         this.x = schematic.getInt("Width");
         this.y = schematic.getInt("Height");
         this.z = schematic.getInt("Length");
         byte[] blocks = schematic.getByteArray("Blocks");
         byte[] additional = null;
         if (schematic.contains("AddBlocks")) {
            byte[] addBlocks = schematic.getByteArray("AddBlocks");
            additional = new byte[addBlocks.length * 2];

            for (int i = 0; i < addBlocks.length; i++) {
               additional[i * 2 + 0] = (byte)(addBlocks[i] >> 4 & 15);
               additional[i * 2 + 1] = (byte)(addBlocks[i] >> 0 & 15);
            }
         }

         this.states = new BlockState[this.x][this.z][this.y];

         for (int y = 0; y < this.y; y++) {
            for (int z = 0; z < this.z; z++) {
               for (int x = 0; x < this.x; x++) {
                  int blockInd = (y * this.z + z) * this.x + x;
                  int blockID = blocks[blockInd] & 255;
                  if (additional != null) {
                     blockID |= additional[blockInd] << 8;
                  }

                  Block block = (Block)BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(ItemIdFix.getItem(blockID)));
                  this.states[x][z][y] = block.defaultBlockState();
               }
            }
         }
      }
   }
}
