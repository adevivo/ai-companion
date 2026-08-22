package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.commands.base.GotoTarget;
import com.player2.playerengine.tasks.movement.DefaultGoToDimensionTask;
import com.player2.playerengine.tasks.movement.GetToBlockTask;
import com.player2.playerengine.tasks.movement.GetToXZTask;
import com.player2.playerengine.tasks.movement.GetToYTask;
import com.player2.playerengine.tasks.base.Task;
import net.minecraft.core.BlockPos;

public class GotoCommand extends Command {
   public GotoCommand() throws CommandException {
      super(
         "goto",
         "Tell bot to travel to a set of coordinates",
         new Arg<>(GotoTarget.class, "[x y z dimension]/[x z dimension]/[y dimension]/[dimension]/[x y z]/[x z]/[y]")
      );
   }

   public static Task getMovementTaskFor(GotoTarget target) {
      return (Task)(switch (target.getType()) {
         case XYZ -> new GetToBlockTask(new BlockPos(target.getX(), target.getY(), target.getZ()), target.getDimension());
         case XZ -> new GetToXZTask(target.getX(), target.getZ(), target.getDimension());
         case Y -> new GetToYTask(target.getY(), target.getDimension());
         case NONE -> new DefaultGoToDimensionTask(target.getDimension());
      });
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      GotoTarget target = parser.get(GotoTarget.class);
      mod.runUserTask(getMovementTaskFor(target), () -> this.finish());
   }
}
