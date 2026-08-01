package adris.altoclef.util.time;

import net.minecraft.server.MinecraftServer;

/**
 * Tick clock backing {@link TimerGame}.
 *
 * <p>Replaces the original client-only source ({@code Minecraft.getInstance().getConnection()}),
 * which does not exist in a dedicated server jar and threw {@code NoClassDefFoundError} on every
 * companion tick there.
 *
 * <p>{@link MinecraftServer#getTickCount()} is used deliberately in preference to
 * {@code Level.getGameTime()}. Tick count starts at 0 when a server starts, which matches the
 * behaviour of the connection counter it replaces (single player builds a fresh integrated server
 * per world load). Game time is the world's <em>persisted</em> age, so on an established world it
 * would start in the tens of thousands of seconds and every freshly constructed timer — all of
 * which begin at {@code prevTime = 0} — would report elapsed on its very first check.
 *
 * <p>Nothing here may throw. {@code TimerGame} is read from the {@code BlockScanner} rescan worker
 * thread inside a {@code finally} block, where an exception would skip the {@code scanning = false}
 * reset and wedge the scanner permanently.
 */
public final class ServerClock {

   /** Written from the server thread, read from worker threads — see class javadoc. */
   private static volatile MinecraftServer server;

   /** Last value handed out, so we can answer without a server instead of throwing. */
   private static volatile double lastKnownSeconds = 0.0;

   private ServerClock() {
   }

   /**
    * Point the clock at a running server. Safe to call repeatedly; the controller does so from its
    * constructor and from the server tick hook so that we never depend on {@code SERVER_STARTED}
    * having been registered before it fired.
    */
   public static void attach(MinecraftServer toAttach) {
      if (toAttach != null) {
         server = toAttach;
      }
   }

   /** Drop the reference on shutdown so a stopped server cannot leak into the next world. */
   public static void detach() {
      MinecraftServer outgoing = server;
      if (outgoing != null) {
         lastKnownSeconds = secondsOf(outgoing);
      }

      server = null;
   }

   /** The attached server, or null between shutdown and the next world load. */
   public static MinecraftServer current() {
      return server;
   }

   /** Seconds last reported by any server, for use when none is attached. */
   public static double lastKnownSeconds() {
      return lastKnownSeconds;
   }

   /**
    * Seconds since {@code target} started ticking. Passing a stopped server is intentional and
    * supported — {@link TimerGame} reads the outgoing server's final value to re-base timers when
    * the clock restarts from zero underneath them.
    */
   public static double secondsOf(MinecraftServer target) {
      if (target == null) {
         return lastKnownSeconds;
      }

      double seconds = target.getTickCount() / 20.0;
      lastKnownSeconds = seconds;
      return seconds;
   }
}
