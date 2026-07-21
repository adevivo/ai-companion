package baritone.api.cache;

import java.util.Set;

public interface IWaypointCollection {
   void addWaypoint(IWaypoint var1);

   void removeWaypoint(IWaypoint var1);

   IWaypoint getMostRecentByTag(IWaypoint.Tag var1);

   Set<IWaypoint> getByTag(IWaypoint.Tag var1);

   Set<IWaypoint> getAllWaypoints();
}
