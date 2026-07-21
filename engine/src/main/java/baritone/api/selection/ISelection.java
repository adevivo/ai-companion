package baritone.api.selection;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

public interface ISelection {
   BetterBlockPos pos1();

   BetterBlockPos pos2();

   BetterBlockPos min();

   BetterBlockPos max();

   Vec3i size();

   AABB aabb();

   ISelection expand(Direction var1, int var2);

   ISelection contract(Direction var1, int var2);

   ISelection shift(Direction var1, int var2);
}
