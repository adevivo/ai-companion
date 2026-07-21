package adris.altoclef.eventbus.events;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PlayerCollidedWithEntityEvent {
   public Player player;
   public Entity other;

   public PlayerCollidedWithEntityEvent(Player player, Entity other) {
      this.player = player;
      this.other = other;
   }
}
