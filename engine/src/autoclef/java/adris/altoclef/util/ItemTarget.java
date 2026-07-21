package adris.altoclef.util;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.Item;

public class ItemTarget {
   private static final int BASICALLY_INFINITY = 99999999;
   public static ItemTarget EMPTY = new ItemTarget(new Item[0], 0);
   private Item[] itemMatches;
   private final int targetCount;
   private String catalogueName = null;
   private boolean infinite = false;

   public static ItemTarget[] of(Item... items) {
      return Arrays.stream(items).map(ItemTarget::new).toArray(ItemTarget[]::new);
   }

   public ItemTarget(Item[] items, int targetCount) {
      this.itemMatches = items;
      this.targetCount = targetCount;
      this.infinite = false;
   }

   public ItemTarget(String catalogueName, int targetCount) {
      this.catalogueName = catalogueName;
      this.itemMatches = TaskCatalogue.getItemMatches(catalogueName);
      this.targetCount = targetCount;
   }

   public ItemTarget(String catalogueName) {
      this(catalogueName, 1);
   }

   public ItemTarget(Item item, int targetCount) {
      this(new Item[]{item}, targetCount);
   }

   public ItemTarget(Item... items) {
      this(items, 1);
   }

   public ItemTarget(Item item) {
      this(item, 1);
   }

   public ItemTarget(ItemTarget toCopy, int newCount) {
      if (toCopy.itemMatches != null) {
         this.itemMatches = new Item[toCopy.itemMatches.length];
         System.arraycopy(toCopy.itemMatches, 0, this.itemMatches, 0, toCopy.itemMatches.length);
      }

      this.catalogueName = toCopy.catalogueName;
      this.targetCount = newCount;
      this.infinite = toCopy.infinite;
   }

   public static boolean nullOrEmpty(ItemTarget target) {
      return target == null || target == EMPTY;
   }

   public static Item[] getMatches(ItemTarget... targets) {
      Set<Item> result = new HashSet<>();

      for (ItemTarget target : targets) {
         result.addAll(Arrays.asList(target.getMatches()));
      }

      return result.toArray(Item[]::new);
   }

   public ItemTarget infinite() {
      this.infinite = true;
      return this;
   }

   public Item[] getMatches() {
      return this.itemMatches != null ? this.itemMatches : new Item[0];
   }

   public int getTargetCount() {
      return this.infinite ? 99999999 : this.targetCount;
   }

   public boolean matches(Item item) {
      if (this.itemMatches != null) {
         for (Item match : this.itemMatches) {
            if (match != null && match.equals(item)) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean isCatalogueItem() {
      return this.catalogueName != null;
   }

   public String getCatalogueName() {
      return this.catalogueName;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj instanceof ItemTarget other) {
         if (this.infinite) {
            if (!other.infinite) {
               return false;
            }
         } else if (this.targetCount != other.targetCount) {
            return false;
         }

         if (other.itemMatches == null != (this.itemMatches == null)) {
            return false;
         } else {
            if (this.itemMatches != null) {
               if (this.itemMatches.length != other.itemMatches.length) {
                  return false;
               }

               for (int i = 0; i < this.itemMatches.length; i++) {
                  if (other.itemMatches[i] == null) {
                     if (other.itemMatches[i] == null != (this.itemMatches[i] == null)) {
                        return false;
                     }
                  } else if (!other.itemMatches[i].equals(this.itemMatches[i])) {
                     return false;
                  }
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public boolean isEmpty() {
      return this.itemMatches == null || this.itemMatches.length == 0;
   }

   @Override
   public String toString() {
      StringBuilder result = new StringBuilder();
      if (this.isEmpty()) {
         result.append("(empty)");
      } else if (this.isCatalogueItem()) {
         result.append(this.catalogueName);
      } else {
         result.append("[");
         int counter = 0;
         if (this.itemMatches != null) {
            for (Item item : this.itemMatches) {
               if (item == null) {
                  result.append("(null??)");
               } else {
                  result.append(ItemHelper.trimItemName(item.getDescriptionId()));
               }

               if (++counter != this.itemMatches.length) {
                  result.append(",");
               }
            }
         }

         result.append("]");
      }

      if (!this.infinite && !this.isEmpty() && this.targetCount > 1) {
         result.append(" x ").append(this.targetCount);
      } else if (this.infinite) {
         result.append(" x infinity");
      }

      return result.toString();
   }
}
