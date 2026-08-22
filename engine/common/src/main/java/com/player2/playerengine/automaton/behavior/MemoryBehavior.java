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

package com.player2.playerengine.automaton.behavior;

import com.player2.playerengine.automaton.Baritone;
import com.player2.playerengine.automaton.api.cache.IWaypoint;
import com.player2.playerengine.automaton.api.cache.Waypoint;
import com.player2.playerengine.automaton.api.event.events.BlockInteractEvent;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import net.minecraft.world.level.block.BedBlock;

public final class MemoryBehavior extends Behavior {
   public MemoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onBlockInteract(BlockInteractEvent event) {
      if (event.getType() == BlockInteractEvent.Type.USE && BlockStateInterface.getBlock(this.ctx, event.getPos()) instanceof BedBlock) {
         this.baritone
            .getWorldProvider()
            .getCurrentWorld()
            .getWaypoints()
            .addWaypoint(new Waypoint("bed", IWaypoint.Tag.BED, BetterBlockPos.from(event.getPos())));
      }
   }
}
