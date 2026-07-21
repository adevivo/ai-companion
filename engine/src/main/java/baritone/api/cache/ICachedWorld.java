package baritone.api.cache;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;

public interface ICachedWorld {
   boolean isCached(int var1, int var2);

   ArrayList<BlockPos> getLocationsOf(String var1, int var2, int var3, int var4, int var5);
}
