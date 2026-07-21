package adris.altoclef.util.time;

public class Stopwatch {
   boolean running = false;
   private double startTime = 0.0;

   private static double currentTime() {
      return System.currentTimeMillis() / 1000.0;
   }

   public void begin() {
      this.startTime = currentTime();
      this.running = true;
   }

   public double time() {
      return !this.running ? 0.0 : currentTime() - this.startTime;
   }
}
