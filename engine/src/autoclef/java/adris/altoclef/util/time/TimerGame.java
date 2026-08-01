package adris.altoclef.util.time;

import adris.altoclef.Debug;
import net.minecraft.server.MinecraftServer;

/**
 * A timer measured in server ticks.
 *
 * <p>The clock restarts from zero every time a server starts, so a timer that outlives one server
 * would otherwise see its {@code prevTime} stranded in the old timebase, making {@link #getDuration}
 * negative and {@link #elapsed} false forever. The re-basing below is the guard against that; it is
 * the same trick the original client build used when a player reconnected and the connection tick
 * counter reset.
 */
public class TimerGame extends BaseTimer {
   private MinecraftServer lastServer;

   public TimerGame(double intervalSeconds) {
      super(intervalSeconds);
   }

   @Override
   protected double currentTime() {
      MinecraftServer currentServer = ServerClock.current();
      if (currentServer == null) {
         // Either the server is shutting down or a worker thread raced world unload. Report the
         // last time we saw rather than throwing, and leave lastServer alone so a transient gap
         // does not get mistaken for a restart and re-base the timer.
         return ServerClock.lastKnownSeconds();
      }

      if (currentServer != this.lastServer) {
         if (this.lastServer != null) {
            double prevTimeTotal = ServerClock.secondsOf(this.lastServer);
            Debug.logInternal("(TimerGame: New server detected, offsetting by " + prevTimeTotal + " seconds)");
            this.setPrevTimeForce(this.getPrevTime() - prevTimeTotal);
         }

         this.lastServer = currentServer;
      }

      return ServerClock.secondsOf(currentServer);
   }
}
