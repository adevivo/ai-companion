package adris.altoclef.util.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class GoalFollowEntity implements Goal {
   private final Entity entity;
   private final double closeEnoughDistance;

   public GoalFollowEntity(Entity entity, double closeEnoughDistance) {
      this.entity = entity;
      this.closeEnoughDistance = closeEnoughDistance;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      BlockPos p = new BlockPos(x, y, z);
      return this.entity.blockPosition().equals(p) || p.closerToCenterThan(this.entity.position(), this.closeEnoughDistance);
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double xDiff = x - this.entity.position().x();
      int yDiff = y - this.entity.blockPosition().getY();
      double zDiff = z - this.entity.position().z();
      return GoalBlock.calculate(xDiff, yDiff, zDiff);
   }
}
