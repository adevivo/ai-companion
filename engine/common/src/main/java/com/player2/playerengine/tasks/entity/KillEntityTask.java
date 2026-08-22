package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.Entity;

public class KillEntityTask extends AbstractKillEntityTask {
   private final Entity target;

   public KillEntityTask(Entity entity) {
      this.target = entity;
   }

   public KillEntityTask(Entity entity, double maintainDistance, double combatGuardLowerRange, double combatGuardLowerFieldRadius) {
      super(maintainDistance, combatGuardLowerRange, combatGuardLowerFieldRadius);
      this.target = entity;
   }

   @Override
   protected Optional<Entity> getEntityTarget(PlayerEngineController mod) {
      return Optional.of(this.target);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof KillEntityTask task ? Objects.equals(task.target, this.target) : false;
   }

   @Override
   protected String toDebugString() {
      return "Killing " + this.target.getType().getDescriptionId();
   }
}
