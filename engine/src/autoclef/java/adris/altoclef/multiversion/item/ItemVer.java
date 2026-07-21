package adris.altoclef.multiversion.item;

import adris.altoclef.multiversion.FoodComponentWrapper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class ItemVer {
   public static FoodComponentWrapper getFoodComponent(Item item) {
      return FoodComponentWrapper.of(item.getFoodProperties());
   }

   public static boolean isFood(ItemStack stack) {
      return isFood(stack.getItem());
   }

   public static boolean hasCustomName(ItemStack stack) {
      return stack.hasCustomHoverName();
   }

   public static boolean isFood(Item item) {
      return item.isEdible();
   }

   private static boolean isSuitableFor(Item item, BlockState state) {
      return item.isCorrectToolForDrops(state);
   }

   private static Item RAW_GOLD() {
      return Items.RAW_GOLD;
   }

   private static Item RAW_IRON() {
      return Items.RAW_IRON;
   }
}
