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
import com.player2.playerengine.automaton.api.selection.ISelectionManager;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import java.util.LinkedList;
import java.util.ListIterator;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

public class SelectionManager implements ISelectionManager {
   private final Entity holder;
   private final LinkedList<ISelection> selections = new LinkedList<>();
   private ISelection[] selectionsArr = new ISelection[0];

   public SelectionManager(Entity holder) {
      this.holder = holder;
   }

   private void resetSelectionsArr() {
      this.selectionsArr = this.selections.toArray(new ISelection[0]);
   }

   @Override
   public synchronized ISelection addSelection(ISelection selection) {
      this.selections.add(selection);
      this.resetSelectionsArr();
      return selection;
   }

   @Override
   public ISelection addSelection(BetterBlockPos pos1, BetterBlockPos pos2) {
      return this.addSelection(new Selection(pos1, pos2));
   }

   @Override
   public synchronized ISelection removeSelection(ISelection selection) {
      this.selections.remove(selection);
      this.resetSelectionsArr();
      return selection;
   }

   @Override
   public synchronized ISelection[] removeAllSelections() {
      ISelection[] selectionsArr = this.getSelections();
      this.selections.clear();
      this.resetSelectionsArr();
      return selectionsArr;
   }

   @Override
   public ISelection[] getSelections() {
      return this.selectionsArr;
   }

   @Override
   public synchronized ISelection getOnlySelection() {
      return this.selections.size() == 1 ? this.selections.peekFirst() : null;
   }

   @Override
   public ISelection getLastSelection() {
      return this.selections.peekLast();
   }

   @Override
   public synchronized ISelection expand(ISelection selection, Direction direction, int blocks) {
      ListIterator<ISelection> it = this.selections.listIterator();

      while (it.hasNext()) {
         ISelection current = it.next();
         if (current == selection) {
            it.remove();
            it.add(current.expand(direction, blocks));
            this.resetSelectionsArr();
            return it.previous();
         }
      }

      return null;
   }

   @Override
   public synchronized ISelection contract(ISelection selection, Direction direction, int blocks) {
      ListIterator<ISelection> it = this.selections.listIterator();

      while (it.hasNext()) {
         ISelection current = it.next();
         if (current == selection) {
            it.remove();
            it.add(current.contract(direction, blocks));
            this.resetSelectionsArr();
            return it.previous();
         }
      }

      return null;
   }

   @Override
   public synchronized ISelection shift(ISelection selection, Direction direction, int blocks) {
      ListIterator<ISelection> it = this.selections.listIterator();

      while (it.hasNext()) {
         ISelection current = it.next();
         if (current == selection) {
            it.remove();
            it.add(current.shift(direction, blocks));
            this.resetSelectionsArr();
            return it.previous();
         }
      }

      return null;
   }
}
