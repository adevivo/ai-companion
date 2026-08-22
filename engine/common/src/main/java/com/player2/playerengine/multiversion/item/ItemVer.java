package com.player2.playerengine.multiversion.item;

import com.player2.playerengine.multiversion.FoodComponentWrapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class ItemVer {
   public static FoodComponentWrapper getFoodComponent(Item item) {
      return FoodComponentWrapper.of(item.components().get(DataComponents.FOOD));
   }

   public static boolean isFood(ItemStack stack) {
      return isFood(stack.getItem());
   }

   public static boolean hasCustomName(ItemStack stack) {
      return stack.has(DataComponents.CUSTOM_NAME);
   }

   public static boolean isFood(Item item) {
      return item.components().has(DataComponents.FOOD);
   }

   private static boolean isSuitableFor(Item item, BlockState state) {
      return item.isCorrectToolForDrops(new ItemStack(item), state);
   }

   private static Item RAW_GOLD() {
      return Items.RAW_GOLD;
   }

   private static Item RAW_IRON() {
      return Items.RAW_IRON;
   }
}
