package adris.altoclef.eventbus.events;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class EntityDeathEvent {
   public Entity entity;
   public DamageSource damageSource;

   public EntityDeathEvent(Entity entity, DamageSource damageSource) {
      this.entity = entity;
      this.damageSource = damageSource;
   }
}
