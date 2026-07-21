package baritone.api.pathing.goals;

import net.minecraft.core.BlockPos;

public interface Goal {
   boolean isInGoal(int var1, int var2, int var3);

   double heuristic(int var1, int var2, int var3);

   default boolean isInGoal(BlockPos pos) {
      return this.isInGoal(pos.getX(), pos.getY(), pos.getZ());
   }

   default double heuristic(BlockPos pos) {
      return this.heuristic(pos.getX(), pos.getY(), pos.getZ());
   }

   default double heuristic() {
      return 0.0;
   }
}
