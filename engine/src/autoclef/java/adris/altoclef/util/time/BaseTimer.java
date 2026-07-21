package adris.altoclef.util.time;

public abstract class BaseTimer {
   private double prevTime = 0.0;
   private double interval;

   public BaseTimer(double intervalSeconds) {
      this.interval = intervalSeconds;
   }

   public double getDuration() {
      return this.currentTime() - this.prevTime;
   }

   public void setInterval(double interval) {
      this.interval = interval;
   }

   public boolean elapsed() {
      return this.getDuration() > this.interval;
   }

   public void reset() {
      this.prevTime = this.currentTime();
   }

   public void forceElapse() {
      this.prevTime = 0.0;
   }

   protected abstract double currentTime();

   protected void setPrevTimeForce(double toSet) {
      this.prevTime = toSet;
   }

   protected double getPrevTime() {
      return this.prevTime;
   }
}
