package baritone.api.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockOptionalMetaLookup {
   private final BlockOptionalMeta[] boms;

   public BlockOptionalMetaLookup(BlockOptionalMeta... boms) {
      this.boms = boms;
   }

   public BlockOptionalMetaLookup(ServerLevel world, Block... blocks) {
      this.boms = Stream.of(blocks).map(block -> new BlockOptionalMeta(world, block)).toArray(BlockOptionalMeta[]::new);
   }

   public BlockOptionalMetaLookup(ServerLevel world, List<Block> blocks) {
      this.boms = blocks.stream().map(block -> new BlockOptionalMeta(world, block)).toArray(BlockOptionalMeta[]::new);
   }

   public BlockOptionalMetaLookup(ServerLevel world, String... blocks) {
      this.boms = Stream.of(blocks).map(block -> new BlockOptionalMeta(world, block)).toArray(BlockOptionalMeta[]::new);
   }

   public boolean has(Block block) {
      for (BlockOptionalMeta bom : this.boms) {
         if (bom.getBlock() == block) {
            return true;
         }
      }

      return false;
   }

   public boolean has(BlockState state) {
      for (BlockOptionalMeta bom : this.boms) {
         if (bom.matches(state)) {
            return true;
         }
      }

      return false;
   }

   public boolean has(ItemStack stack) {
      for (BlockOptionalMeta bom : this.boms) {
         if (bom.matches(stack)) {
            return true;
         }
      }

      return false;
   }

   public List<BlockOptionalMeta> blocks() {
      return Arrays.asList(this.boms);
   }

   @Override
   public String toString() {
      return String.format("BlockOptionalMetaLookup{%s}", Arrays.toString((Object[])this.boms));
   }
}
