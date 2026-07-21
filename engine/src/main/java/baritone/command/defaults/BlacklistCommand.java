package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.process.IGetToBlockProcess;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class BlacklistCommand extends Command {
   public BlacklistCommand() {
      super("blacklist");
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      args.requireMax(0);
      IGetToBlockProcess proc = baritone.getGetToBlockProcess();
      if (!proc.isActive()) {
         throw new CommandInvalidStateException("GetToBlockProcess is not currently active");
      } else if (proc.blacklistClosest()) {
         this.logDirect(source, "Blacklisted closest instances");
      } else {
         throw new CommandInvalidStateException("No known locations, unable to blacklist");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Blacklist closest block";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "While going to a block this command blacklists the closest block so that block finding processes won't attempt to get to it.",
         "",
         "Usage:",
         "> blacklist"
      );
   }
}
