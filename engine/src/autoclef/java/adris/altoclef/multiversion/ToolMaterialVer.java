package adris.altoclef.multiversion;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

public class ToolMaterialVer {
   public static int getMiningLevel(TieredItem item) {
      return getMiningLevel(item.getTier());
   }

   public static int getMiningLevel(Tier material) {
      if (material.equals(Tiers.WOOD) || material.equals(Tiers.GOLD)) {
         return 0;
      } else if (material.equals(Tiers.STONE)) {
         return 1;
      } else if (material.equals(Tiers.IRON)) {
         return 2;
      } else if (material.equals(Tiers.DIAMOND)) {
         return 3;
      } else if (material.equals(Tiers.NETHERITE)) {
         return 4;
      } else {
         throw new IllegalStateException("Unexpected value: " + material);
      }
   }
}
