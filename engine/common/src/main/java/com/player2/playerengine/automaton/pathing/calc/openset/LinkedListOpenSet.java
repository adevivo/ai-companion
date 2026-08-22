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

class LinkedListOpenSet implements IOpenSet {
   private LinkedListOpenSet.Node first = null;

   @Override
   public boolean isEmpty() {
      return this.first == null;
   }

   @Override
   public void insert(PathNode pathNode) {
      LinkedListOpenSet.Node node = new LinkedListOpenSet.Node();
      node.val = pathNode;
      node.nextOpen = this.first;
      this.first = node;
   }

   @Override
   public void update(PathNode node) {
   }

   @Override
   public PathNode removeLowest() {
      if (this.first == null) {
         return null;
      } else {
         LinkedListOpenSet.Node current = this.first.nextOpen;
         if (current == null) {
            LinkedListOpenSet.Node n = this.first;
            this.first = null;
            return n.val;
         } else {
            LinkedListOpenSet.Node previous = this.first;
            double bestValue = this.first.val.combinedCost;
            LinkedListOpenSet.Node bestNode = this.first;

            LinkedListOpenSet.Node beforeBest;
            for (beforeBest = null; current != null; current = current.nextOpen) {
               double comp = current.val.combinedCost;
               if (comp < bestValue) {
                  bestValue = comp;
                  bestNode = current;
                  beforeBest = previous;
               }

               previous = current;
            }

            if (beforeBest == null) {
               this.first = this.first.nextOpen;
               bestNode.nextOpen = null;
               return bestNode.val;
            } else {
               beforeBest.nextOpen = bestNode.nextOpen;
               bestNode.nextOpen = null;
               return bestNode.val;
            }
         }
      }
   }

   public static class Node {
      private LinkedListOpenSet.Node nextOpen;
      private PathNode val;
   }
}
