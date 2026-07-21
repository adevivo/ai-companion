package baritone.api.cache;

import java.util.Map;
import net.minecraft.core.BlockPos;

public interface IContainerMemory {
   IRememberedInventory getInventoryByPos(BlockPos var1);

   Map<BlockPos, IRememberedInventory> getRememberedInventories();
}
