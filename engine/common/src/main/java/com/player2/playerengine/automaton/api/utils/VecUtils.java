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

package com.player2.playerengine.automaton.api.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VecUtils {
   private VecUtils() {
   }

   public static Vec3 calculateBlockCenter(Level world, BlockPos pos) {
      BlockState b = world.getBlockState(pos);
      VoxelShape shape = b.getCollisionShape(world, pos);
      if (shape.isEmpty()) {
         return getBlockPosCenter(pos);
      } else {
         double xDiff = (shape.min(Axis.X) + shape.max(Axis.X)) / 2.0;
         double yDiff = (shape.min(Axis.Y) + shape.max(Axis.Y)) / 2.0;
         double zDiff = (shape.min(Axis.Z) + shape.max(Axis.Z)) / 2.0;
         if (!Double.isNaN(xDiff) && !Double.isNaN(yDiff) && !Double.isNaN(zDiff)) {
            if (b.getBlock() instanceof BaseFireBlock) {
               yDiff = 0.0;
            }

            return new Vec3(pos.getX() + xDiff, pos.getY() + yDiff, pos.getZ() + zDiff);
         } else {
            throw new IllegalStateException(b + " " + pos + " " + shape);
         }
      }
   }

   public static Vec3 getBlockPosCenter(BlockPos pos) {
      return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
   }

   public static double distanceToCenter(BlockPos pos, double x, double y, double z) {
      double xdiff = pos.getX() + 0.5 - x;
      double ydiff = pos.getY() + 0.5 - y;
      double zdiff = pos.getZ() + 0.5 - z;
      return Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
   }

   public static double entityDistanceToCenter(Entity entity, BlockPos pos) {
      return distanceToCenter(pos, entity.getX(), entity.getY(), entity.getZ());
   }

   public static double entityFlatDistanceToCenter(Entity entity, BlockPos pos) {
      return distanceToCenter(pos, entity.getX(), pos.getY() + 0.5, entity.getZ());
   }
}
