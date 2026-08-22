package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.tasks.base.Task;
import java.util.List;

public class StatusCommand extends Command {
   public StatusCommand() {
      super("status", "Get status of currently executing command");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      List<Task> tasks = mod.getUserTaskChain().getTasks();
      if (tasks.isEmpty()) {
         mod.log("No tasks currently running.");
      } else {
         mod.log("CURRENT TASK: " + tasks.get(0).toString());
      }

      this.finish();
   }
}
