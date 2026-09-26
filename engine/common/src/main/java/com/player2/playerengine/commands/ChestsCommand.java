package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.container.SurveyContainersTask;

/**
 * `chests`: walk to the nearby chests, barrels and shulker boxes, look inside, and report what is in
 * them. The answer reaches the agent in the command's finish event, not the rolling debug buffer.
 */
public class ChestsCommand extends Command {
   public ChestsCommand() throws CommandException {
      super(
         "chests",
         "Walk to the chests, barrels and shulker boxes within 16 blocks, look inside, and report what each one holds. Use it before `withdraw` when you do not know where something is, or when asked what is in the chests."
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      SurveyContainersTask task = new SurveyContainersTask();
      mod.runUserTask(task, () -> {
         mod.reportCommandResult(task.report());
         this.finish();
      });
   }
}
