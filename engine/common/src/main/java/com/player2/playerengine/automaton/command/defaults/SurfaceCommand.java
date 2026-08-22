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

package com.player2.playerengine.automaton.command.defaults;

import com.player2.playerengine.automaton.api.IBaritone;
import com.player2.playerengine.automaton.api.command.Command;
import com.player2.playerengine.automaton.api.command.argument.IArgConsumer;
import com.player2.playerengine.automaton.api.command.exception.CommandException;
import com.player2.playerengine.automaton.api.pathing.goals.Goal;
import com.player2.playerengine.automaton.api.pathing.goals.GoalBlock;
import com.player2.playerengine.automaton.api.utils.BetterBlockPos;
import com.player2.playerengine.automaton.api.utils.IEntityContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.AirBlock;

public class SurfaceCommand extends Command {
   protected SurfaceCommand() {
      super("surface", "top");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      IEntityContext ctx = baritone.getEntityContext();
      BetterBlockPos playerPos = ctx.feetPos();
      int surfaceLevel = ctx.world().getSeaLevel();
      int worldHeight = ctx.world().getHeight();
      if (playerPos.getY() > surfaceLevel && ctx.world().getBlockState(playerPos.up()).getBlock() instanceof AirBlock) {
         this.logDirect(source, "Already at surface");
      } else {
         int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

         for (int currentIteratedY = startingYPos; currentIteratedY < worldHeight; currentIteratedY++) {
            BetterBlockPos newPos = new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());
            if (!(ctx.world().getBlockState(newPos).getBlock() instanceof AirBlock) && newPos.getY() > playerPos.getY()) {
               Goal goal = new GoalBlock(newPos.up());
               this.logDirect(source, String.format("Going to: %s", goal.toString()));
               baritone.getCustomGoalProcess().setGoalAndPath(goal);
               return;
            }
         }

         this.logDirect(source, "No higher location found");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Used to get out of caves, mines, ...";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The surface/top command makes an entity head towards the closest surface-like area.",
         "",
         "This can be the surface or the highest available air space, depending on circumstances.",
         "",
         "Usage:",
         "> surface - Used to get out of caves, mines, ...",
         "> top - Used to get out of caves, mines, ..."
      );
   }
}
