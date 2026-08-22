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

package com.player2.playerengine.automaton.pathing.calc.openset;

import com.player2.playerengine.automaton.pathing.calc.PathNode;
import java.util.Arrays;

public final class BinaryHeapOpenSet implements IOpenSet {
   private static final int INITIAL_CAPACITY = 1024;
   private PathNode[] array;
   private int size = 0;

   public BinaryHeapOpenSet() {
      this(1024);
   }

   public BinaryHeapOpenSet(int size) {
      this.array = new PathNode[size];
   }

   public int size() {
      return this.size;
   }

   @Override
   public final void insert(PathNode value) {
      if (this.size >= this.array.length - 1) {
         this.array = Arrays.copyOf(this.array, this.array.length << 1);
      }

      this.size++;
      value.heapPosition = this.size;
      this.array[this.size] = value;
      this.update(value);
   }

   @Override
   public final void update(PathNode val) {
      int index = val.heapPosition;
      int parentInd = index >>> 1;
      double cost = val.combinedCost;

      for (PathNode parentNode = this.array[parentInd]; index > 1 && parentNode.combinedCost > cost; parentNode = this.array[parentInd]) {
         this.array[index] = parentNode;
         this.array[parentInd] = val;
         val.heapPosition = parentInd;
         parentNode.heapPosition = index;
         index = parentInd;
         parentInd >>>= 1;
      }
   }

   @Override
   public final boolean isEmpty() {
      return this.size == 0;
   }

   @Override
   public final PathNode removeLowest() {
      if (this.size == 0) {
         throw new IllegalStateException();
      } else {
         PathNode result = this.array[1];
         PathNode val = this.array[this.size];
         this.array[1] = val;
         val.heapPosition = 1;
         this.array[this.size] = null;
         this.size--;
         result.heapPosition = -1;
         if (this.size < 2) {
            return result;
         } else {
            int index = 1;
            int smallerChild = 2;
            double cost = val.combinedCost;

            do {
               PathNode smallerChildNode = this.array[smallerChild];
               double smallerChildCost = smallerChildNode.combinedCost;
               if (smallerChild < this.size) {
                  PathNode rightChildNode = this.array[smallerChild + 1];
                  double rightChildCost = rightChildNode.combinedCost;
                  if (smallerChildCost > rightChildCost) {
                     smallerChild++;
                     smallerChildCost = rightChildCost;
                     smallerChildNode = rightChildNode;
                  }
               }

               if (cost <= smallerChildCost) {
                  break;
               }

               this.array[index] = smallerChildNode;
               this.array[smallerChild] = val;
               val.heapPosition = smallerChild;
               smallerChildNode.heapPosition = index;
               index = smallerChild;
            } while ((smallerChild <<= 1) <= this.size);

            return result;
         }
      }
   }
}
