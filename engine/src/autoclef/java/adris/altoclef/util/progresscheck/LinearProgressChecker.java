package adris.altoclef.util.progresscheck;

import adris.altoclef.util.time.TimerGame;

public class LinearProgressChecker implements IProgressChecker<Double> {
   private final double minProgress;
   private final TimerGame timer;
   private double lastProgress;
   private double currentProgress;
   private boolean first;
   private boolean failed;

   public LinearProgressChecker(double timeout, double minProgress) {
      this.minProgress = minProgress;
      this.timer = new TimerGame(timeout);
      this.reset();
   }

   public void setProgress(Double progress) {
      this.currentProgress = progress;
      if (this.first) {
         this.lastProgress = progress;
         this.first = false;
      }

      if (this.timer.elapsed()) {
         double improvement = progress - this.lastProgress;
         if (improvement < this.minProgress) {
            this.failed = true;
         }

         this.first = false;
         this.timer.reset();
         this.lastProgress = progress;
      }
   }

   @Override
   public boolean failed() {
      return this.failed;
   }

   @Override
   public void reset() {
      this.failed = false;
      this.timer.reset();
      this.first = true;
   }
}
