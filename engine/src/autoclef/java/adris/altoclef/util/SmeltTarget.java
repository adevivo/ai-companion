package adris.altoclef.util;

import java.util.Objects;
import net.minecraft.world.item.Item;

public class SmeltTarget {
   private final ItemTarget item;
   private final Item[] optionalMaterials;
   private ItemTarget material;

   public SmeltTarget(ItemTarget item, ItemTarget material, Item... optionalMaterials) {
      this.item = item;
      this.material = material;
      this.material = new ItemTarget(material, this.item.getTargetCount());
      this.optionalMaterials = optionalMaterials;
   }

   public ItemTarget getItem() {
      return this.item;
   }

   public ItemTarget getMaterial() {
      return this.material;
   }

   public Item[] getOptionalMaterials() {
      return this.optionalMaterials;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         SmeltTarget that = (SmeltTarget)o;
         return Objects.equals(this.material, that.material) && Objects.equals(this.item, that.item);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.material, this.item);
   }
}
