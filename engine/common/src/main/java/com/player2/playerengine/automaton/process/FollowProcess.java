/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.process;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalComposite;
import com.player2.playerengine.automaton.api.pathing.goals.GoalNear;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import com.player2.playerengine.automaton.api.process.IFollowProcess;
import com.player2.playerengine.automaton.api.process.PathingCommand;
import com.player2.playerengine.automaton.api.process.PathingCommandType;
import com.player2.playerengine.automaton.utils.BaritoneProcessHelper;
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
