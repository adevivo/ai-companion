package baritone.api.cache;

import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface IWaypoint {
   String getName();

   IWaypoint.Tag getTag();

   long getCreationTimestamp();

   BetterBlockPos getLocation();

   public static enum Tag {
      HOME("home", "base"),
      DEATH("death"),
      BED("bed", "spawn"),
      USER("user");

      private static final List<IWaypoint.Tag> TAG_LIST = Collections.unmodifiableList(Arrays.asList(values()));
      public final String[] names;

      private Tag(String... names) {
         this.names = names;
      }

      public String getName() {
         return this.names[0];
      }

      public static IWaypoint.Tag getByName(String name) {
         for (IWaypoint.Tag action : values()) {
            for (String alias : action.names) {
               if (alias.equalsIgnoreCase(name)) {
                  return action;
               }
            }
         }

         return null;
      }

      public static String[] getAllNames() {
         Set<String> names = new HashSet<>();

         for (IWaypoint.Tag tag : values()) {
            names.addAll(Arrays.asList(tag.names));
         }

         return names.toArray(new String[0]);
      }
   }
}
