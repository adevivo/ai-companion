package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.baritone.GoalChunk;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import baritone.api.pathing.goals.Goal;
import net.minecraft.world.level.ChunkPos;

public class GetToChunkTask extends CustomBaritoneGoalTask {
   private final ChunkPos pos;

   public GetToChunkTask(ChunkPos pos) {
      this.checker = new MovementProgressChecker();
      this.pos = pos;
   }

   @Override
   protected Goal newGoal(AltoClefController mod) {
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
