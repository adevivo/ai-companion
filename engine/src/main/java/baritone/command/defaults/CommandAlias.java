package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.command.argument.ArgConsumer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;

public class CommandAlias extends Command {
   private final String shortDesc;
   public final String target;

   public CommandAlias(List<String> names, String shortDesc, String target) {
      super(names.toArray(new String[0]));
      this.shortDesc = shortDesc;
      this.target = target;
   }

   public CommandAlias(String name, String shortDesc, String target) {
      super(name);
      this.shortDesc = shortDesc;
      this.target = target;
   }

   @Override
   public void execute(CommandSourceStack source, String label, IArgConsumer args, IBaritone baritone) throws CommandException {
      baritone.getCommandManager().execute(source, String.format("%s %s", this.target, args.rawRest()));
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return ((ArgConsumer)args).getBaritone().getCommandManager().tabComplete(String.format("%s %s", this.target, args.rawRest()));
   }

   @Override
   public String getShortDesc() {
      return this.shortDesc;
   }

   @Override
   public List<String> getLongDesc() {
      return Collections.singletonList(String.format("This command is an alias, for: %s ...", this.target));
   }
}
