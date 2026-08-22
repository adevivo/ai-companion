package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.player2api.manager.ConversationManager;

public class ResetMemoryCommand extends Command {
   public ResetMemoryCommand() {
      super("resetmemory", "Reset the memory, does not stop the agent, can ONLY be run by the user (NOT the agent).");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      ConversationManager.resetMemory(mod);
      this.finish();
   }
}
