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
               getException.accept(new CommandException("Invalid command:" + part));
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
            throw new CommandException("Command " + command + " does not exist.");
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

   public Command get(String name) {
      return this.commandSheet.getOrDefault(name, null);
   }
}
