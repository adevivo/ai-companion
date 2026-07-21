package adris.altoclef.commandsystem;

import java.util.ArrayList;
import java.util.List;

public class ArgParser {
   private final ArgBase[] args;
   int argCounter;
   int unitCounter;
   String[] argUnits;

   public ArgParser(ArgBase... args) {
      this.args = args;
      this.argCounter = 0;
      this.unitCounter = 0;
   }

   public static List<String> splitLineIntoKeywords(String line) {
      List<String> result = new ArrayList<>();
      String last_kword = "";
      boolean open_quote = false;
      char prev_char = 0;

      for (char c : line.toCharArray()) {
         if (c == '"') {
            open_quote = !open_quote;
         }

         if (prev_char == '\\') {
            if (c == '#' || c == '"') {
               last_kword = last_kword.substring(0, last_kword.length() - 1);
            }
         } else if (c == '#') {
            break;
         }

         if (c == ' ' && !open_quote) {
            if (last_kword.length() != 0) {
               result.add(last_kword.trim());
            }

            last_kword = "";
         } else {
            last_kword = last_kword + c;
         }

         prev_char = c;
      }

      if (last_kword.length() != 0) {
         result.add(last_kword.trim());
      }

      return result;
   }

   public void loadArgs(String line, boolean removeFirst) {
      List<String> units = splitLineIntoKeywords(line);
      if (removeFirst && units.size() != 0) {
         units.remove(0);
      }

      this.argUnits = new String[units.size()];
      units.toArray(this.argUnits);
      this.argCounter = 0;
      this.unitCounter = 0;
   }

   public <T> T get(Class<T> type) throws CommandException {
      if (this.argCounter >= this.args.length) {
         throw new CommandException("You tried grabbing more arguments than you had... Bad move.");
      } else {
         ArgBase arg = this.args[this.argCounter];
         if (!arg.isArbitrarilyLong() && this.argUnits.length > this.args.length) {
            throw new CommandException(String.format("Too many arguments provided %d. The maximum is %d.", this.argUnits.length, this.args.length));
         } else {
            this.argCounter++;
            if (arg.isArray()) {
               this.argCounter = this.args.length;
            }

            int givenArgs = this.argUnits.length;
            if (arg.hasDefault() && arg.getMinArgCountToUseDefault() >= givenArgs) {
               return arg.getDefault(type);
            } else if (this.unitCounter >= this.argUnits.length) {
               throw new CommandException(String.format("Not enough arguments supplied: You supplied %d.", this.argUnits.length));
            } else {
               String unit = this.argUnits[this.unitCounter];
               String[] unitPlusRemaining = new String[this.argUnits.length - this.unitCounter];
               System.arraycopy(this.argUnits, this.unitCounter, unitPlusRemaining, 0, unitPlusRemaining.length);
               this.unitCounter++;
               return arg.parseUnit(unit, unitPlusRemaining);
            }
         }
      }
   }

   public ArgBase[] getArgs() {
      return this.args;
   }

   public String[] getArgUnits() {
      return this.argUnits;
   }
}
