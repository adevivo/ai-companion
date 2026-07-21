package adris.altoclef.tasksystem;

import net.minecraft.world.entity.LivingEntity;

public interface ITaskRequiresGrounded extends ITaskCanForce {
   @Override
   default boolean shouldForce(Task interruptingCandidate) {
      if (interruptingCandidate instanceof ITaskOverridesGrounded) {
         return false;
      } else {
         LivingEntity player = ((Task)this).controller.getPlayer();
         return !player.onGround() && !player.isSwimming() && !player.isInWater() && !player.onClimbable();
      }
   }
}
