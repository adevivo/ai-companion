package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;
import com.player2.playerengine.tasks.entity.BodyguardTask;

/**
 * Protect the owner until told to stop. See {@link BodyguardTask} for why this is a task rather than
 * a skill the model follows turn by turn.
 */
public class BodyguardCommand extends Command {
   public BodyguardCommand() {
      super("bodyguard",
            "Stay beside your owner and attack any hostile mob that threatens them (creepers first),"
                  + " continuously and without further instructions, until they tell you to stop."
                  + " Use it for 'protect me', 'guard me', 'watch my back', 'bodyguard'.");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      if (mod.getOwner() == null) {
         mod.reportCommandResult("Cannot bodyguard: your owner is not here.");
         this.finish();
         return;
      }
      mod.runUserTask(new BodyguardTask(), () -> this.finish());
   }
}
