package baritone.utils.accessor;

import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

public interface ServerChunkManagerAccessor {
   @Nullable
   LevelChunk automatone$getChunkNow(int var1, int var2);
}
