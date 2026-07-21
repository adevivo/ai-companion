package baritone.api.event.events;

import java.util.function.Function;

public final class TickEvent {
   private static int overallTickCount;
   private final TickEvent.Type type;
   private final int count;

   public TickEvent(TickEvent.Type type, int count) {
      this.type = type;
      this.count = count;
   }

   public int getCount() {
      return this.count;
   }

   public TickEvent.Type getType() {
      return this.type;
   }

   public static synchronized Function<TickEvent.Type, TickEvent> createNextProvider() {
      int count = overallTickCount++;
      return type -> new TickEvent(type, count);
   }

   public static enum Type {
      IN,
      OUT;
   }
}
