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

   /**
    * When the task started, so a player who is never found becomes a report rather than silence.
    *
    * <p>Standing in "doing nothing until player loads" is indistinguishable from following, and the
    * agent will happily say it is following while it happens — observed with a name whose case did
    * not match. The case matching is fixed at the tracker, but a wrong name is still possible and
    * must not fail quietly.
    */
   private long startedAtNanos = 0L;
   private boolean reportedMissing = false;

   private static final long MISSING_PLAYER_GRACE_NANOS = 10_000_000_000L;

   @Override
   protected void onStart() {
      this.startedAtNanos = System.nanoTime();
      this.reportedMissing = false;
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      Optional<Vec3> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(this.playerName);
      if (lastPos.isEmpty()) {
         this.setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
         if (!this.reportedMissing && System.nanoTime() - this.startedAtNanos > MISSING_PLAYER_GRACE_NANOS) {
            this.reportedMissing = true;
            String known = String.join(", ", mod.getEntityTracker().getAllLoadedPlayerUsernames());
            mod.logAgentInfo(String.format(
                  "Cannot follow \"%s\" — no player by that name is anywhere near. %s Do not claim to be "
                        + "following them; say you cannot find them and ask which player you should follow.",
                  this.playerName,
                  known.isBlank() ? "No players are loaded at all." : "Players nearby: " + known + "."));
         }
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
