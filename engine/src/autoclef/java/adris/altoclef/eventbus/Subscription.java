package adris.altoclef.eventbus;

import java.util.function.Consumer;

public class Subscription<T> {
   private final Consumer<T> callback;
   private boolean shouldDelete;

   public Subscription(Consumer<T> callback) {
      this.callback = callback;
   }

   public void accept(T event) {
      this.callback.accept(event);
   }

   public void delete() {
      this.shouldDelete = true;
   }

   public boolean shouldDelete() {
      return this.shouldDelete;
   }
}
