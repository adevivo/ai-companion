package baritone.api.process;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface IFarmProcess extends IBaritoneProcess {
   void farm(int var1, BlockPos var2);

   /**
    * Why the farm is not currently making progress, or {@code null} when it is working.
    *
    * <p>Exists so the layer above can tell the owner the truth. A farm task reports {@code <Farming ...>}
    * for as long as it is armed, whether or not anything is happening, so an agent reading only the task
    * status will confidently narrate a harvest that is not occurring — which is exactly what happened on
    * 2026-07-28. "Waiting for the crops to regrow" and "stuck with no block scan" both look identical
    * from outside and need very different responses.
    */
   @Nullable
   default String getStallReason() {
      return null;
   }

   default void farm() {
      this.farm(0, null);
   }

   default void farm(int range) {
      this.farm(range, null);
   }
}
