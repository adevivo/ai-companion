package adris.altoclef.commandsystem;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;

public abstract class Command {
   private final ArgParser parser;
   private final String name;
   private final String description;
   private AltoClefController mod;
   private Runnable onFinish = null;

   public Command(String name, String description, ArgBase... args) {
      this.name = name;
      this.description = description;
      this.parser = new ArgParser(args);
   }

   public void run(AltoClefController mod, String line, Runnable onFinish) throws CommandException {
      this.onFinish = onFinish;
      this.mod = mod;
      this.parser.loadArgs(line, true);
      this.call(mod, this.parser);
   }

   protected void finish() {
      if (this.onFinish != null) {
         this.onFinish.run();
      }
   }

   public String getHelpRepresentation() {
      StringBuilder sb = new StringBuilder(this.name);

      for (ArgBase arg : this.parser.getArgs()) {
         sb.append(" ");
         sb.append(arg.getHelpRepresentation());
      }

      return sb.toString();
   }

   protected void log(Object message) {
      Debug.logMessage(message.toString());
   }

   protected void logError(Object message) {
      Debug.logError(message.toString());
   }

   protected abstract void call(AltoClefController var1, ArgParser var2) throws CommandException;

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }
}
