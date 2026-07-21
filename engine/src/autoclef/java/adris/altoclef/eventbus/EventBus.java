package adris.altoclef.eventbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.Tuple;

public class EventBus {
   private static final HashMap<Class, List<Subscription>> topics = new HashMap<>();
   private static final List<Tuple<Class, Subscription>> toAdd = new ArrayList<>();
   private static boolean lock;

   public static <T> void publish(T event) {
      Class<?> type = event.getClass();

      for (Tuple<Class, Subscription> toAdd : EventBus.toAdd) {
         subscribeInternal((Class<T>)toAdd.getA(), (Subscription<T>)toAdd.getB());
      }

      EventBus.toAdd.clear();
      if (topics.containsKey(type)) {
         List<Subscription> subscribers = topics.get(type);
         List<Subscription> toDelete = new ArrayList<>();
         lock = true;

         for (Subscription<T> subRaw : subscribers) {
            try {
               if (subRaw.shouldDelete()) {
                  toDelete.add(subRaw);
               } else {
                  subRaw.accept(event);
               }
            } catch (ClassCastException var7) {
               System.err.println("TRIED PUBLISHING MISMAPPED EVENT: " + event);
               var7.printStackTrace();
            }
         }

         lock = false;
      }
   }

   private static <T> void subscribeInternal(Class<T> type, Subscription<T> sub) {
      if (!topics.containsKey(type)) {
         topics.put(type, new ArrayList<>());
      }

      topics.get(type).add(sub);
   }

   public static <T> Subscription<T> subscribe(Class<T> type, Consumer<T> consumeEvent) {
      Subscription<T> sub = new Subscription<>(consumeEvent);
      if (lock) {
         toAdd.add(new Tuple(type, sub));
      } else {
         subscribeInternal(type, sub);
      }

      return sub;
   }

   public static <T> void unsubscribe(Subscription<T> subscription) {
      if (subscription != null) {
         subscription.delete();
      }
   }
}
