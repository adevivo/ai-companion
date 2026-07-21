package adris.altoclef.tasks.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import java.util.Optional;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Items;

public class ShearSheepTask extends AbstractDoToEntityTask {
   public ShearSheepTask() {
      super(0.0, -1.0, -1.0);
   }

   @Override
   protected boolean isSubEqual(AbstractDoToEntityTask other) {
      return other instanceof ShearSheepTask;
   }

   @Override
   protected Task onEntityInteract(AltoClefController mod, Entity entity) {
      if (!mod.getItemStorage().hasItem(Items.SHEARS)) {
         Debug.logWarning("Failed to shear sheep because you have no shears.");
         return null;
      } else {
         if (mod.getSlotHandler().forceEquipItem(Items.SHEARS)) {
            ((Sheep)entity).shear(SoundSource.PLAYERS);
            mod.getPlayer().getMainHandItem().hurtAndBreak(1, mod.getPlayer(), e -> {});
         }

         return null;
      }
   }

   @Override
   protected Optional<Entity> getEntityTarget(AltoClefController mod) {
      return mod.getEntityTracker()
         .getClosestEntity(
            mod.getPlayer().position(), entity -> !(entity instanceof Sheep sheep) ? false : sheep.readyForShearing() && !sheep.isSheared(), Sheep.class
         );
   }

   @Override
   protected String toDebugString() {
      return "Shearing Sheep";
   }
}
