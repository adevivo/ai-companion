package adris.altoclef.util.slots;

import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public class Slot {
   public static final int CURSOR_SLOT_INDEX = -1;
   private static final int UNDEFINED_SLOT_INDEX = -999;
   public static final Slot UNDEFINED = new Slot(null, -999);
   private final NonNullList<ItemStack> inventory;
   private final int index;

   public Slot(NonNullList<ItemStack> inventory, int index) {
      this.inventory = inventory;
      this.index = index;
   }

   public NonNullList<ItemStack> getInventory() {
      return this.inventory;
   }

   public int getIndex() {
      return this.index;
   }

   public static boolean isCursor(Slot slot) {
      return slot instanceof CursorSlot;
   }

   public ItemStack getStack() {
      return this.inventory != null && this.index >= 0 && this.index < this.inventory.size() ? (ItemStack)this.inventory.get(this.index) : ItemStack.EMPTY;
   }

   @Deprecated
   public int getInventorySlot() {
      return this.index;
   }

   @Deprecated
   public int getWindowSlot() {
      return -1;
   }

   protected String getName() {
      return this.inventory == null ? "Special" : this.inventory.getClass().getSimpleName();
   }

   @Override
   public String toString() {
      return this.getName()
         + " Slot {inventory="
         + (this.inventory != null ? this.inventory.getClass().getSimpleName() : "null")
         + ", index="
         + this.index
         + "}";
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Slot slot = (Slot)o;
         return this.index == slot.index && Objects.equals(this.inventory, slot.inventory);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.inventory, this.index);
   }
}
