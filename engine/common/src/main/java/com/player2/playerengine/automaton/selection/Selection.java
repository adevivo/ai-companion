/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.player2.playerengine.automaton.selection;

import com.player2.playerengine.automaton.api.selection.ISelection;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
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
      this.aabb = new AABB(this.min.getCenter(), this.max.offset(1, 1, 1).getCenter());
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
