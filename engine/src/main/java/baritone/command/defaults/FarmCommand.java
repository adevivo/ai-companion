package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.cache.IWaypoint;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForWaypoints;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class FarmCommand extends Command {
   public FarmCommand() {
      super("farm");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(2);
      int range = 0;
      BetterBlockPos origin = null;
      if (args.has(1)) {
         range = args.getAs(Integer.class);
      }

      if (args.has(1)) {
         IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
         switch (waypoints.length) {
            case 0:
               throw new CommandInvalidStateException("No waypoints found");
            case 1:
               IWaypoint waypoint = waypoints[0];
               origin = waypoint.getLocation();
               break;
            default:
               throw new CommandInvalidStateException("Multiple waypoints were found");
         }
      }

      baritone.getFarmProcess().farm(range, origin);
      this.logDirect(source, "Farming");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Farm nearby crops";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The farm command starts farming nearby plants. It harvests mature crops and plants new ones.",
         "",
         "Usage:",
         "> farm - farms every crop it can find.",
         "> farm <range> - farm crops within range from the starting position.",
         "> farm <range> <waypoint> - farm crops within range from waypoint."
      );
   }
}
