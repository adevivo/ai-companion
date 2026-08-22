package com.player2.playerengine.commands;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.commands.base.ArgParser;
import com.player2.playerengine.commands.base.Command;
import com.player2.playerengine.util.helpers.ConfigHelper;

public class ReloadSettingsCommand extends Command {
   public ReloadSettingsCommand() {
      super("reload_settings", "Reloads bot settings and butler whitelist/blacklist.");
   }

   @Override
   protected void call(PlayerEngineController mod, ArgParser parser) {
      ConfigHelper.reloadAllConfigs();
      mod.log("Reload successful!");
      this.finish();
   }
}
