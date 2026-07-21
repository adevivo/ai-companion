package baritone.api.command.datatypes;

import baritone.api.IBaritone;
import baritone.api.cache.IWaypoint;
import baritone.api.cache.IWaypointCollection;
import baritone.api.command.exception.CommandException;
import baritone.api.command.helpers.TabCompleteHelper;
import java.util.Comparator;
import java.util.stream.Stream;

public enum ForWaypoints implements IDatatypeFor<IWaypoint[]> {
   INSTANCE;

   public IWaypoint[] get(IDatatypeContext ctx) throws CommandException {
      String input = ctx.getConsumer().getString();
      IWaypoint.Tag tag = IWaypoint.Tag.getByName(input);
      return tag == null ? getWaypointsByName(ctx.getBaritone(), input) : getWaypointsByTag(ctx.getBaritone(), tag);
   }

   @Override
   public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
      return new TabCompleteHelper()
         .append(getWaypointNames(ctx.getBaritone()))
         .sortAlphabetically()
         .prepend(IWaypoint.Tag.getAllNames())
         .filterPrefix(ctx.getConsumer().getString())
         .stream();
   }

   public static IWaypointCollection waypoints(IBaritone baritone) {
      return baritone.getWorldProvider().getCurrentWorld().getWaypoints();
   }

   public static IWaypoint[] getWaypoints(IBaritone baritone) {
      return waypoints(baritone)
         .getAllWaypoints()
         .stream()
         .sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp).reversed())
         .toArray(IWaypoint[]::new);
   }

   public static String[] getWaypointNames(IBaritone baritone) {
      return Stream.of(getWaypoints(baritone)).map(IWaypoint::getName).filter(name -> !name.isEmpty()).toArray(String[]::new);
   }

   public static IWaypoint[] getWaypointsByTag(IBaritone baritone, IWaypoint.Tag tag) {
      return waypoints(baritone).getByTag(tag).stream().sorted(Comparator.comparingLong(IWaypoint::getCreationTimestamp).reversed()).toArray(IWaypoint[]::new);
   }

   public static IWaypoint[] getWaypointsByName(IBaritone baritone, String name) {
      return Stream.of(getWaypoints(baritone)).filter(waypoint -> waypoint.getName().equalsIgnoreCase(name)).toArray(IWaypoint[]::new);
   }
}
