package adris.altoclef.commands;

import adris.altoclef.AltoClefController;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.util.helpers.ConfigHelper;

public class ReloadSettingsCommand extends Command {
   public ReloadSettingsCommand() {
      super("reload_settings", "Reloads bot settings and butler whitelist/blacklist.");
   }

   @Override
   protected void call(AltoClefController mod, ArgParser parser) {
      ConfigHelper.reloadAllConfigs();
      mod.log("Reload successful!");
      this.finish();
   }
}
