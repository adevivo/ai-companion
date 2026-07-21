package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.tasksystem.Task;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class FollowPlayerTask extends Task {
   private final String playerName;
   private final double followDistance;

   public FollowPlayerTask(String playerName, double followDistance) {
      this.playerName = playerName;
      this.followDistance = followDistance;
   }

   public FollowPlayerTask(String playerName) {
      this(playerName, 2.0);
   }

   @Override
   protected void onStart() {
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      Optional<Vec3> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(this.playerName);
      if (lastPos.isEmpty()) {
         this.setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
         return null;
      } else {
         Vec3 target = lastPos.get();
         if (target.closerThan(mod.getPlayer().position(), 1.0) && !mod.getEntityTracker().isPlayerLoaded(this.playerName)) {
            mod.logWarning("Failed to get to player \"" + this.playerName + "\". We moved to where we last saw them but now have no idea where they are.");
            this.stop();
            return null;
         } else {
            Optional<Player> player = mod.getEntityTracker().getPlayerEntity(this.playerName);
            return (Task)(player.isEmpty()
               ? new GetToBlockTask(new BlockPos((int)target.x, (int)target.y, (int)target.z), false)
               : new GetToEntityTask((Entity)player.get(), this.followDistance));
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
   }

   @Override
   protected boolean isEqual(Task other) {
      return !(other instanceof FollowPlayerTask task)
         ? false
         : task.playerName.equals(this.playerName) && Math.abs(this.followDistance - task.followDistance) < 0.1;
   }

   @Override
   protected String toDebugString() {
      return "Going to player " + this.playerName;
   }
}
