package adris.altoclef.util;

import adris.altoclef.Debug;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum MiningRequirement implements Comparable<MiningRequirement> {
   HAND(Items.AIR),
   WOOD(Items.WOODEN_PICKAXE),
   STONE(Items.STONE_PICKAXE),
   IRON(Items.IRON_PICKAXE),
   DIAMOND(Items.DIAMOND_PICKAXE);

   private final Item minPickaxe;

   private MiningRequirement(Item minPickaxe) {
      this.minPickaxe = minPickaxe;
   }

   public static MiningRequirement getMinimumRequirementForBlock(Block block) {
      if (block.defaultBlockState().requiresCorrectToolForDrops()) {
         for (MiningRequirement req : values()) {
            if (req != HAND) {
               Item pick = req.getMinimumPickaxe();
               if (pick.isCorrectToolForDrops(block.defaultBlockState())) {
                  return req;
               }
            }
         }

         Debug.logWarning(
            "Failed to find ANY effective tool against: " + block + ". I assume netherite is not required anywhere, so something else probably went wrong."
         );
         return DIAMOND;
      } else {
         return HAND;
      }
   }

   public Item getMinimumPickaxe() {
      return this.minPickaxe;
   }
}
