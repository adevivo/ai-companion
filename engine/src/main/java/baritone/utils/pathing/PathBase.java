package baritone.utils.pathing;

import baritone.api.Settings;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.pathing.path.CutoffPath;
import baritone.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;

public abstract class PathBase implements IPath {
   public PathBase cutoffAtLoadedChunks(BlockStateInterface bsi, Settings settings) {
      if (!settings.cutoffAtLoadBoundary.get()) {
         return this;
      } else {
         for (int i = 0; i < this.positions().size(); i++) {
            BlockPos pos = this.positions().get(i);
            if (!bsi.worldContainsLoadedChunk(pos.getX(), pos.getZ())) {
               return new CutoffPath(this, i);
            }
         }

         return this;
      }
   }

   public PathBase staticCutoff(Goal destination, Settings settings) {
      int minLength = settings.pathCutoffMinimumLength.get();
      double cutoffFactor = settings.pathCutoffFactor.get();
      if (this.length() < minLength) {
         return this;
      } else if (destination != null && !destination.isInGoal(this.getDest())) {
         int newLength = (int)((this.length() - minLength) * cutoffFactor) + minLength - 1;
         return new CutoffPath(this, newLength);
      } else {
         return this;
      }
   }
}
