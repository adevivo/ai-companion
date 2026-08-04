package adris.altoclef.commandsystem;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.Consumer;

public class CommandExecutor {
   private final HashMap<String, Command> commandSheet = new HashMap<>();
   private final AltoClefController mod;

   public CommandExecutor(AltoClefController mod) {
      this.mod = mod;
   }

   public void registerNewCommand(Command... commands) {
      for (Command command : commands) {
         if (this.commandSheet.containsKey(command.getName())) {
            Debug.logInternal("Command with name " + command.getName() + " already exists! Can't register that name twice.");
         } else {
            this.commandSheet.put(command.getName(), command);
         }
      }
   }

   public String getCommandPrefix() {
      return this.mod.getModSettings().getCommandPrefix();
   }

   public boolean isClientCommand(String line) {
      return line.startsWith(this.getCommandPrefix());
   }

   private void executeRecursive(Command[] commands, String[] parts, int index, Runnable onFinish, Consumer<CommandException> getException) {
      if (index >= commands.length) {
         onFinish.run();
      } else {
         Command command = commands[index];
         String part = parts[index];

         try {
            if (command == null) {
               getException.accept(new CommandException("Invalid command: " + part
                     + ". The valid commands are: "
                     + this.commandSheet.keySet().stream().sorted().collect(java.util.stream.Collectors.joining(", "))
                     + ". Pick one of these or generate an empty command \"\"."));
               this.executeRecursive(commands, parts, index + 1, onFinish, getException);
            } else {
               command.run(this.mod, part, () -> this.executeRecursive(commands, parts, index + 1, onFinish, getException));
            }
         } catch (CommandException var9) {
            getException.accept(new CommandException(var9.getMessage() + "\nUsage: " + command.getHelpRepresentation(), var9));
         }
      }
   }

   public void execute(String line, Runnable onFinish, Consumer<CommandException> getException) {
      if (this.isClientCommand(line)) {
         line = line.substring(this.getCommandPrefix().length());
         String[] parts = line.split(";");
         Command[] commands = new Command[parts.length];

         try {
            for (int i = 0; i < parts.length; i++) {
               commands[i] = this.getCommand(parts[i]);
            }
         } catch (CommandException var7) {
            getException.accept(var7);
         }

         this.executeRecursive(commands, parts, 0, onFinish, getException);
      }
   }

   public void execute(String line, Consumer<CommandException> getException) {
      this.execute(line, () -> {}, getException);
   }

   public void execute(String line) {
      this.execute(line, ex -> Debug.logWarning(ex.getMessage()));
   }

   public void executeWithPrefix(String line) {
      if (!line.startsWith(this.getCommandPrefix())) {
         line = this.getCommandPrefix() + line;
      }

      this.execute(line);
   }

   private Command getCommand(String line) throws CommandException {
      line = line.trim();
      if (line.length() != 0) {
         String command = line;
         int firstSpace = line.indexOf(32);
         if (firstSpace != -1) {
            command = line.substring(0, firstSpace);
         }

         if (!this.commandSheet.containsKey(command)) {
            // Name the alternatives. The reader is a language model, and "does not exist" alone gives
            // it nothing to correct towards — it re-issues the same invented command until something
            // stops it. Observed with `dig`, `home-guard` and `position`, the last two invented from
            // words that appear in a skill's prose.
            throw new CommandException("Command " + command + " does not exist. The valid commands are: "
                  + this.commandSheet.keySet().stream().sorted().collect(java.util.stream.Collectors.joining(", "))
                  + ". Pick one of these or generate an empty command \"\".");
         } else {
            return this.commandSheet.get(command);
         }
      } else {
         return null;
      }
   }

   public Collection<Command> allCommands() {
      return this.commandSheet.values();
   }

   /**
    * Commands the owner may run but the companion may not choose for itself.
    *
    * <p>These are plumbing, not behaviour. {@code resetmemory} wipes the conversation history and
    * {@code chatclef} switches the brain off entirely — both irreversible from the model's side, and
    * both something it could plausibly talk itself into ("a fresh start seems best here"). {@code
    * reload_settings} re-reads config and {@code gamer} launches a beat-the-game routine that has
    * nothing to do with being somebody's companion.
    *
    * <p>Two of the four already describe themselves to the model as "can ONLY be run by the user (NOT
    * the agent)" — that instruction was the only thing enforcing it, which is to say nothing was.
    */
   public static final java.util.Set<String> OWNER_ONLY =
         java.util.Set.of("resetmemory", "chatclef", "reload_settings", "gamer");

   /** Whether {@code name} is owner-only plumbing the companion must not run. See {@link #OWNER_ONLY}. */
   public static boolean isOwnerOnly(String name) {
      return name != null && OWNER_ONLY.contains(name.trim().toLowerCase(java.util.Locale.ROOT));
   }

   /**
    * The commands the companion is allowed to pick from — everything except {@link #OWNER_ONLY}.
    *
    * <p>This is what belongs in the system prompt. Advertising a command the model is not permitted to
    * run wastes prompt on every single call and invites it to try.
    */
   public Collection<Command> agentCommands() {
      return this.commandSheet.values().stream().filter(c -> !isOwnerOnly(c.getName())).toList();
   }

   public Command get(String name) {
      return this.commandSheet.getOrDefault(name, null);
   }
}
