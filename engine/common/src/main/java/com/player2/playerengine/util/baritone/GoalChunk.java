package com.player2.playerengine.util.baritone;

import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalXZ;
import net.minecraft.world.level.ChunkPos;

public class GoalChunk implements Goal {
   private final ChunkPos pos;

   public GoalChunk(ChunkPos pos) {
      this.pos = pos;
   }

   @Override
   public boolean isInGoal(int x, int y, int z) {
      return this.pos.getMinBlockX() <= x && x <= this.pos.getMaxBlockX() && this.pos.getMinBlockZ() <= z && z <= this.pos.getMaxBlockZ();
   }

   @Override
   public double heuristic(int x, int y, int z) {
      double cx = (this.pos.getMinBlockX() + this.pos.getMaxBlockX()) / 2.0;
      double cz = (this.pos.getMinBlockZ() + this.pos.getMaxBlockZ()) / 2.0;
      return GoalXZ.calculate(cx - x, cz - z);
   }
}
