package adris.altoclef.trackers.storage;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public enum ContainerType {
   CHEST,
   ENDER_CHEST,
   SHULKER,
   FURNACE,
   BREWING,
   MISC,
   EMPTY;

   public static ContainerType getFromBlock(Block block) {
      if (block instanceof ChestBlock) {
         return CHEST;
      } else if (block instanceof AbstractFurnaceBlock) {
         return FURNACE;
      } else if (block.equals(Blocks.ENDER_CHEST)) {
         return ENDER_CHEST;
      } else if (block instanceof ShulkerBoxBlock) {
         return SHULKER;
      } else if (block instanceof BrewingStandBlock) {
         return BREWING;
      } else {
         return !(block instanceof BarrelBlock) && !(block instanceof DispenserBlock) && !(block instanceof HopperBlock) ? EMPTY : MISC;
      }
   }
}
