package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.misc.FarmTask;
import com.player2.playerengine.tasks.base.Task;
import net.minecraft.core.BlockPos;

public class FarmCommand extends Command {
   public FarmCommand() throws CommandException {
      super(
         "farm",
         "Starts farming nearby crops automatically within range.  Example: `farm 10` to farm crops withing a range of 10 blocks",
         new Arg<>(Integer.class, "range")
      );
   }

   @Override
   protected void call(PlayerEngineController controller, ArgParser parser) throws CommandException {
      Integer range = parser.get(Integer.class);
      BlockPos origin = controller.getEntity().blockPosition();
      Task farmTask = new FarmTask(range, origin);
      controller.runUserTask(farmTask, () -> this.finish());
   }
}
