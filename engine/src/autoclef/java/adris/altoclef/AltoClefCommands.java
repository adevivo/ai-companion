package adris.altoclef;

import adris.altoclef.commands.AttackPlayerOrMobCommand;
import adris.altoclef.commands.BodyLanguageCommand;
import adris.altoclef.commands.DepositCommand;
import adris.altoclef.commands.EquipCommand;
import adris.altoclef.commands.FarmCommand;
import adris.altoclef.commands.FishCommand;
import adris.altoclef.commands.FollowCommand;
import adris.altoclef.commands.FoodCommand;
import adris.altoclef.commands.GamerCommand;
import adris.altoclef.commands.GetCommand;
import adris.altoclef.commands.GiveCommand;
import adris.altoclef.commands.GotoCommand;
import adris.altoclef.commands.HeroCommand;
import adris.altoclef.commands.IdleCommand;
import adris.altoclef.commands.LocateStructureCommand;
import adris.altoclef.commands.MeatCommand;
import adris.altoclef.commands.ReloadSettingsCommand;
import adris.altoclef.commands.ResetMemoryCommand;
import adris.altoclef.commands.SetAIBridgeEnabledCommand;
import adris.altoclef.commands.StopCommand;
import adris.altoclef.commands.random.ScanCommand;
import adris.altoclef.commands.BuildStructureCommand;
import adris.altoclef.commandsystem.CommandException;

public class AltoClefCommands {
   public static void init(AltoClefController controller) throws CommandException {
      controller.getCommandExecutor()
            .registerNewCommand(
                  new GetCommand(),
                  new EquipCommand(),
                  new BuildStructureCommand(),
                  new BodyLanguageCommand(),
                  new DepositCommand(),
                  new GotoCommand(),
                  new IdleCommand(),
                  new HeroCommand(),
                  new LocateStructureCommand(),
                  new StopCommand(),
                  new FoodCommand(),
                  new MeatCommand(),
                  new ReloadSettingsCommand(),
                  new ResetMemoryCommand(),
                  new GamerCommand(),
                  new FollowCommand(),
                  new GiveCommand(),
                  new ScanCommand(),
                  new AttackPlayerOrMobCommand(),
                  new SetAIBridgeEnabledCommand(),
                  new FarmCommand(),
                  new FishCommand());
   }
}
