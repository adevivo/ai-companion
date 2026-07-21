package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.process.ICustomGoalProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class InvertCommand extends Command {
   public InvertCommand() {
      super("invert");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      ICustomGoalProcess customGoalProcess = baritone.getCustomGoalProcess();
      Goal goal;
      if ((goal = customGoalProcess.getGoal()) == null) {
         throw new CommandInvalidStateException("No goal");
      } else {
         if (goal instanceof GoalInverted) {
            goal = ((GoalInverted)goal).origin;
         } else {
            goal = new GoalInverted(goal);
         }

         customGoalProcess.setGoalAndPath(goal);
         this.logDirect(source, String.format("Goal: %s", goal.toString()));
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Run away from the current goal";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The invert command tells Automatone to head away from the current goal rather than towards it.", "", "Usage:", "> invert - Invert the current goal."
      );
   }
}
