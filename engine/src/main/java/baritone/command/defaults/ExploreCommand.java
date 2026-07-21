package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeGoalXZ;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ExploreCommand extends Command {
   public ExploreCommand() {
      super("explore");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      if (args.hasAny()) {
         args.requireExactly(2);
      } else {
         args.requireMax(0);
      }

      BetterBlockPos feetPos = baritone.getEntityContext().feetPos();
      GoalXZ goal = args.hasAny() ? args.getDatatypePost(RelativeGoalXZ.INSTANCE, feetPos) : new GoalXZ(feetPos);
      baritone.getExploreProcess().explore(goal.getX(), goal.getZ());
      this.logDirect(source, String.format("Exploring from %s", goal.toString()));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return args.hasAtMost(2) ? args.tabCompleteDatatype(RelativeGoalXZ.INSTANCE) : Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Explore things";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Tell Automatone to explore randomly. If you used explorefilter before this, it will be applied.",
         "",
         "Usage:",
         "> explore - Explore from your current position.",
         "> explore <x> <z> - Explore from the specified X and Z position."
      );
   }
}
