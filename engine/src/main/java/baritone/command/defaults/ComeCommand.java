package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.GoalBlock;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class ComeCommand extends Command {
   public ComeCommand() {
      super("come");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(BlockPos.containing(source.getPosition())));
      this.logDirect(source, "Coming");
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Start heading towards your camera";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The come command tells Automatone to head towards the position at which the command was executed.",
         "",
         "This can be useful alongside redirection commands like \"/execute\".",
         "",
         "Usage:",
         "> come"
      );
   }
}
