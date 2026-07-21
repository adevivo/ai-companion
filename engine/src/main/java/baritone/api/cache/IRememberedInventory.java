package baritone.api.cache;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public interface IRememberedInventory {
   List<ItemStack> getContents();

   int getSize();
}
