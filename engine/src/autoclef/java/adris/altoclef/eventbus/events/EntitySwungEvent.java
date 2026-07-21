package adris.altoclef.eventbus.events;

import net.minecraft.world.entity.Entity;

public class EntitySwungEvent {
   public Entity entity;

   public EntitySwungEvent(Entity entity) {
      this.entity = entity;
   }
}
