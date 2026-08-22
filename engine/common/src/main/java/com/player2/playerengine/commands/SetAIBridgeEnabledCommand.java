package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.commands.base.Arg;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.commands.base.CommandException;

public class SetAIBridgeEnabledCommand extends Command {
   public SetAIBridgeEnabledCommand() throws CommandException {
      super(
         "chatclef",
         "Turns chatclef on or off, can ONLY be run by the user (NOT the agent).",
         new Arg<>(SetAIBridgeEnabledCommand.ToggleState.class, "onOrOff")
      );
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) throws CommandException {
      SetAIBridgeEnabledCommand.ToggleState toggle = parser.get(SetAIBridgeEnabledCommand.ToggleState.class);
      switch (toggle) {
         case ON:
            Debug.logMessage(
               "Enabling the AI Bridge! You can now hear the player again and will intercept their messages, give them a quick welcome back message."
            );
            mod.setChatClefEnabled(true);
            break;
         case OFF:
            Debug.logMessage("AI Bridge disabled! Say goodbye to the player as you won't hear or intercept any of their messages until they turn you back on.");
            mod.setChatClefEnabled(false);
      }

      this.finish();
   }

   public static enum ToggleState {
      ON,
      OFF;
   }
}
