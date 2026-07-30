package adris.altoclef.mixins;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the mob a target goal belongs to.
 *
 * <p>Exists because {@code mob} is declared on {@link TargetGoal} rather than on the subclass
 * {@code CompanionTargetableMixin} targets, and the mixin annotation processor only verifies shadows
 * against the class named in {@code @Mixin} — an inherited shadow compiles with a warning and relies on
 * runtime hierarchy resolution. An accessor interface on the declaring class is checked properly.
 */
@Mixin({TargetGoal.class})
public interface TargetGoalAccessor {
   @Accessor(value = "mob")
   Mob getGoalMob();
}
