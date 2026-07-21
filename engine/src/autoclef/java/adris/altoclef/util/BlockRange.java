package adris.altoclef.util;

import adris.altoclef.AltoClefController;
import adris.altoclef.util.helpers.WorldHelper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public class BlockRange {
   public BlockPos start;
   public BlockPos end;
   public Dimension dimension = Dimension.OVERWORLD;

   private BlockRange() {
   }

   public BlockRange(BlockPos start, BlockPos end, Dimension dimension) {
      this.start = start;
      this.end = end;
      this.dimension = dimension;
   }

   public boolean contains(AltoClefController controller, BlockPos pos) {
      return this.contains(pos, WorldHelper.getCurrentDimension(controller));
   }

   public boolean isValid() {
      return this.start != null && this.end != null;
   }

   public boolean contains(BlockPos pos, Dimension dimension) {
      return this.dimension != dimension
         ? false
         : this.start.getX() <= pos.getX()
            && pos.getX() <= this.end.getX()
            && this.start.getZ() <= pos.getZ()
            && pos.getZ() <= this.end.getZ()
            && this.start.getY() <= pos.getY()
            && pos.getY() <= this.end.getY();
   }

   @JsonIgnore
   public BlockPos getCenter() {
      BlockPos sum = this.start.offset(this.end);
      return new BlockPos(sum.getX() / 2, sum.getY() / 2, sum.getZ() / 2);
   }

   @Override
   public String toString() {
      return "[" + this.start.toShortString() + " -> " + this.end.toShortString() + ", (" + this.dimension + ")]";
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         BlockRange that = (BlockRange)o;
         return Objects.equals(this.start, that.start) && Objects.equals(this.end, that.end);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.start, this.end);
   }
}
