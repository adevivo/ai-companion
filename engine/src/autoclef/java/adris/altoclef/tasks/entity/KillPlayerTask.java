package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class KillPlayerTask extends AbstractKillEntityTask {
   private String playerName;

   public KillPlayerTask(String playerName) {
      this.playerName = playerName;
   }

   public KillPlayerTask(String playerName, double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
      this.playerName = playerName;
   }

   @Override
   protected Optional<Entity> getEntityTarget(AltoClefController mod) {
      for (Entity entity : this.controller.getWorld().getAllEntities()) {
         if (entity.isAlive() && entity instanceof Player) {
            String playerName = entity.getName().getString().toLowerCase();
            if (playerName != null && playerName.equals(this.playerName.toLowerCase())) {
               return Optional.of(entity);
            }
         }
      }

      return Optional.empty();
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof KillPlayerTask task ? Objects.equals(task.playerName, this.playerName) : false;
   }

   @Override
   protected String toDebugString() {
      return "Killing Player " + this.playerName;
   }
}
