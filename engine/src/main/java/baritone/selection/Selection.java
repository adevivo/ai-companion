package baritone.selection;

import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.AABB;

public class Selection implements ISelection {
   private final BetterBlockPos pos1;
   private final BetterBlockPos pos2;
   private final BetterBlockPos min;
   private final BetterBlockPos max;
   private final Vec3i size;
   private final AABB aabb;

   public Selection(BetterBlockPos pos1, BetterBlockPos pos2) {
      this.pos1 = pos1;
      this.pos2 = pos2;
      this.min = new BetterBlockPos(Math.min(pos1.x, pos2.x), Math.min(pos1.y, pos2.y), Math.min(pos1.z, pos2.z));
      this.max = new BetterBlockPos(Math.max(pos1.x, pos2.x), Math.max(pos1.y, pos2.y), Math.max(pos1.z, pos2.z));
      this.size = new Vec3i(this.max.x - this.min.x + 1, this.max.y - this.min.y + 1, this.max.z - this.min.z + 1);
      this.aabb = new AABB(this.min, this.max.offset(1, 1, 1));
   }

   @Override
   public BetterBlockPos pos1() {
      return this.pos1;
   }

   @Override
   public BetterBlockPos pos2() {
      return this.pos2;
   }

   @Override
   public BetterBlockPos min() {
      return this.min;
   }

   @Override
   public BetterBlockPos max() {
      return this.max;
   }

   @Override
   public Vec3i size() {
      return this.size;
   }

   @Override
   public AABB aabb() {
      return this.aabb;
   }

   @Override
   public int hashCode() {
      return this.pos1.hashCode() ^ this.pos2.hashCode();
   }

   @Override
   public String toString() {
      return String.format("Selection{pos1=%s,pos2=%s}", this.pos1, this.pos2);
   }

   private boolean isPos2(Direction facing) {
      boolean negative = facing.getAxisDirection().getStep() < 0;
      switch (facing.getAxis()) {
         case X:
            return this.pos2.x > this.pos1.x ^ negative;
         case Y:
            return this.pos2.y > this.pos1.y ^ negative;
         case Z:
            return this.pos2.z > this.pos1.z ^ negative;
         default:
            throw new IllegalStateException("Bad Direction.Axis");
      }
   }

   @Override
   public ISelection expand(Direction direction, int blocks) {
      return this.isPos2(direction)
         ? new Selection(this.pos1, this.pos2.offset(direction, blocks))
         : new Selection(this.pos1.offset(direction, blocks), this.pos2);
   }

   @Override
   public ISelection contract(Direction direction, int blocks) {
      return this.isPos2(direction)
         ? new Selection(this.pos1.offset(direction, blocks), this.pos2)
         : new Selection(this.pos1, this.pos2.offset(direction, blocks));
   }

   @Override
   public ISelection shift(Direction direction, int blocks) {
      return new Selection(this.pos1.offset(direction, blocks), this.pos2.offset(direction, blocks));
   }
}
