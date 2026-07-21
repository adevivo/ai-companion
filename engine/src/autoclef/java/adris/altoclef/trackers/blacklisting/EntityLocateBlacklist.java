package adris.altoclef.trackers.blacklisting;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityLocateBlacklist extends AbstractObjectBlacklist<Entity> {
   protected Vec3 getPos(Entity item) {
      return item.position();
   }
}
