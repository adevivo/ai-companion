package baritone.api.utils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class BlockUtils {
   private static transient Map<String, Block> resourceCache = new HashMap<>();

   public static String blockToString(Block block) {
      ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(block);
      String name = loc.getPath();
      if (!loc.getNamespace().equals("minecraft")) {
         name = loc.toString();
      }

      return name;
   }

   public static Block stringToBlockRequired(String name) {
      Block block = stringToBlockNullable(name);
      if (block == null) {
         throw new IllegalArgumentException(String.format("Invalid block name %s", name));
      } else {
         return block;
      }
   }

   public static Block stringToBlockNullable(String name) {
      Block block = resourceCache.get(name);
      if (block != null) {
         return block;
      } else if (resourceCache.containsKey(name)) {
         return null;
      } else {
         block = (Block)BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(name.contains(":") ? name : "minecraft:" + name)).orElse(null);
         Map<String, Block> copy = new HashMap<>(resourceCache);
         copy.put(name, block);
         resourceCache = copy;
         return block;
      }
   }

   private BlockUtils() {
   }
}
