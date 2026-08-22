package com.player2.playerengine.tasks.movement;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.baritone.GoalChunk;
import com.player2.playerengine.util.progresscheck.MovementProgressChecker;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import net.minecraft.world.level.ChunkPos;

public class GetToChunkTask extends CustomBaritoneGoalTask {
   private final ChunkPos pos;

   public GetToChunkTask(ChunkPos pos) {
      this.checker = new MovementProgressChecker();
      this.pos = pos;
   }

   @Override
   protected Goal newGoal(PlayerEngineController mod) {
      return new GoalChunk(this.pos);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof GetToChunkTask task ? task.pos.equals(this.pos) : false;
   }

   @Override
   protected String toDebugString() {
      return "Get to chunk: " + this.pos.toString();
   }
}
