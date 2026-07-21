package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.IEntityContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class ThisWayCommand extends Command {
   public ThisWayCommand() {
      super("thisway", "forward");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireExactly(1);
      IEntityContext ctx = baritone.getEntityContext();
      GoalXZ goal = GoalXZ.fromDirection(ctx.feetPosAsVec(), ctx.entity().yHeadRot, args.getAs(Double.class));
      baritone.getCustomGoalProcess().setGoal(goal);
      this.logDirect(source, String.format("Goal: %s", goal));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Travel in your current direction";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "Creates a GoalXZ some amount of blocks in the direction you're currently looking",
         "",
         "Usage:",
         "> thisway <distance> - makes a GoalXZ distance blocks in front of you"
      );
   }
}
