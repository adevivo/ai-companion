package adris.altoclef.util;

import adris.altoclef.Debug;
import java.util.Arrays;
import net.minecraft.world.item.Item;

public class CraftingRecipe {
   private ItemTarget[] slots;
   private int width;
   private int height;
   private boolean shapeless;
   private String shortName;
   private int outputCount;

   public static CraftingRecipe newShapedRecipe(Item[][] items, int outputCount) {
      return newShapedRecipe(null, items, outputCount);
   }

   public static CraftingRecipe newShapedRecipe(ItemTarget[] slots, int outputCount) {
      return newShapedRecipe(null, slots, outputCount);
   }

   public static CraftingRecipe newShapedRecipe(String shortName, Item[][] items, int outputCount) {
      return newShapedRecipe(shortName, createSlots(items), outputCount);
   }

   public static CraftingRecipe newShapedRecipe(String shortName, ItemTarget[] slots, int outputCount) {
      if (slots.length != 4 && slots.length != 9) {
         Debug.logError("Invalid shaped crafting recipe, must be either size 4 or 9. Size given: " + slots.length);
         return null;
      } else {
         CraftingRecipe result = new CraftingRecipe();
         result.shortName = shortName;
         result.slots = Arrays.stream(slots).map(target -> target == null ? ItemTarget.EMPTY : target).toArray(ItemTarget[]::new);
         result.outputCount = outputCount;
         if (slots.length == 4) {
            result.width = 2;
            result.height = 2;
         } else {
            result.width = 3;
            result.height = 3;
         }

         result.shapeless = false;
         return result;
      }
   }

   private static ItemTarget[] createSlots(ItemTarget[] slots) {
      ItemTarget[] result = new ItemTarget[slots.length];
      System.arraycopy(slots, 0, result, 0, slots.length);
      return result;
   }

   private static ItemTarget[] createSlots(Item[][] slots) {
      ItemTarget[] result = new ItemTarget[slots.length];

      for (int i = 0; i < slots.length; i++) {
         if (slots[i] == null) {
            result[i] = ItemTarget.EMPTY;
         } else {
            result[i] = new ItemTarget(slots[i]);
         }
      }

      return result;
   }

   public ItemTarget getSlot(int index) {
      ItemTarget result = this.slots[index];
      return result != null ? result : ItemTarget.EMPTY;
   }

   public int getSlotCount() {
      return this.slots.length;
   }

   public ItemTarget[] getSlots() {
      return this.slots;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public boolean isShapeless() {
      return this.shapeless;
   }

   public boolean isBig() {
      return this.slots.length > 4;
   }

   public int outputCount() {
      return this.outputCount;
   }

   @Override
   public boolean equals(Object o) {
      if (o instanceof CraftingRecipe other) {
         if (other.shapeless != this.shapeless) {
            return false;
         } else if (other.outputCount != this.outputCount) {
            return false;
         } else if (other.height != this.height) {
            return false;
         } else if (other.width != this.width) {
            return false;
         } else if (other.slots.length != this.slots.length) {
            return false;
         } else {
            for (int i = 0; i < this.slots.length; i++) {
               if (other.slots[i] == null != (this.slots[i] == null)) {
                  return false;
               }

               if (other.slots[i] != null && !other.slots[i].equals(this.slots[i])) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      String name = "CraftingRecipe{";
      if (this.shortName != null) {
         name = name + "craft " + name;
      } else {
         name = name + "_slots=" + name + ", width=" + Arrays.toString((Object[])this.slots) + ", height=" + this.width + ", shapeless=" + this.height;
      }

      return name + "}";
   }
}
