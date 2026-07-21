package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IFollowProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public final class FollowProcess extends BaritoneProcessHelper implements IFollowProcess {
   private Predicate<Entity> filter;
   private List<Entity> cache;

   public FollowProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      this.scanWorld();
      Goal goal = new GoalComposite(this.cache.stream().map(this::towards).toArray(Goal[]::new));
      return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
   }

   private Goal towards(Entity following) {
      BlockPos pos;
      if (this.baritone.settings().followOffsetDistance.get() == 0.0) {
         pos = following.blockPosition();
      } else {
         GoalXZ g = GoalXZ.fromDirection(
            following.position(), this.baritone.settings().followOffsetDirection.get(), this.baritone.settings().followOffsetDistance.get()
         );
         pos = BlockPos.containing(g.getX(), following.getY(), g.getZ());
      }

      return new GoalNear(pos, this.baritone.settings().followRadius.get());
   }

   private boolean followable(Entity entity) {
      if (entity == null) {
         return false;
      } else if (!entity.isAlive()) {
         return false;
      } else {
         return entity.equals(this.ctx.entity()) ? false : entity.equals(this.ctx.world().getEntity(entity.getId()));
      }
   }

   private void scanWorld() {
      this.cache = this.ctx.worldEntitiesStream().filter(this::followable).filter(this.filter).distinct().collect(Collectors.toList());
   }

   @Override
   public boolean isActive() {
      if (this.filter == null) {
         return false;
      } else {
         this.scanWorld();
         return !this.cache.isEmpty();
      }
   }

   @Override
   public void onLostControl() {
      this.filter = null;
      this.cache = null;
   }

   @Override
   public String displayName0() {
      return "Following " + this.cache;
   }

   @Override
   public void follow(Predicate<Entity> filter) {
      this.filter = filter;
   }

   @Override
   public List<Entity> following() {
      return this.cache;
   }

   @Override
   public Predicate<Entity> currentFilter() {
      return this.filter;
   }
}
