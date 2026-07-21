package adris.altoclef.util.time;

public class TimerReal extends BaseTimer {
   public TimerReal(double intervalSeconds) {
      super(intervalSeconds);
   }

   @Override
   protected double currentTime() {
      return System.currentTimeMillis() / 1000.0;
   }
}
