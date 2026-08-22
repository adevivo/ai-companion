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

package com.player2.playerengine.automaton.api.process;

import com.player2.playerengine.automaton.api.utils.BlockOptionalMeta;
import com.player2.playerengine.automaton.api.utils.BlockOptionalMetaLookup;
import net.minecraft.world.level.block.Block;

public interface IMineProcess extends IBaritoneProcess {
   void mineByName(int var1, String... var2);

   void mine(int var1, BlockOptionalMetaLookup var2);

   default void mine(BlockOptionalMetaLookup filter) {
      this.mine(0, filter);
   }

   default void mineByName(String... blocks) {
      this.mineByName(0, blocks);
   }

   default void mine(int quantity, BlockOptionalMeta... boms) {
      this.mine(quantity, new BlockOptionalMetaLookup(boms));
   }

   default void mine(BlockOptionalMeta... boms) {
      this.mine(0, boms);
   }

   void mine(int var1, Block... var2);

   default void mine(Block... blocks) {
      this.mine(0, blocks);
   }

   default void cancel() {
      this.onLostControl();
   }
}
