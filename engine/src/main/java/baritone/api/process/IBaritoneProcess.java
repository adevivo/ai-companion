package baritone.api.process;

public interface IBaritoneProcess {
   double DEFAULT_PRIORITY = -1.0;

   boolean isActive();

   PathingCommand onTick(boolean var1, boolean var2);

   boolean isTemporary();

   void onLostControl();

   default double priority() {
      return -1.0;
   }

   default String displayName() {
      return !this.isActive() ? "INACTIVE" : this.displayName0();
   }

   String displayName0();
}
