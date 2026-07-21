package adris.altoclef.commandsystem;

public class Arg<T> extends ArgBase {
   private final Class<T> tType;
   private final String name;
   public T Default;
   private boolean isArray = false;
   private boolean showDefault;

   public Arg(Class<T> type, String name) throws CommandException {
      this.name = name;
      this.tType = type;
      this.showDefault = true;
      this.hasDefault = false;
      if (!this.tType.isEnum()
         && !this.isInstancesOf(this.tType, String.class, Float.class, Integer.class, Double.class, Long.class, ItemList.class, GotoTarget.class)) {
         throw new CommandException(
            "Arguments are not programmed to parse the following type: "
               + this.tType
               + ". This is either not implemented intentionally or by accident somehow."
         );
      }
   }

   public Arg(Class<T> type, String name, T defaultValue, int minArgCountToUseDefault, boolean showDefault) throws CommandException {
      this(type, name);
      this.hasDefault = true;
      this.Default = defaultValue;
      this.minArgCountToUseDefault = minArgCountToUseDefault;
      this.showDefault = showDefault;
   }

   public Arg(Class<T> type, String name, T defaultValue, int minArgCountToUseDefault) throws CommandException {
      this(type, name, defaultValue, minArgCountToUseDefault, true);
   }

   public static Object parseEnum(String unit, Class type) throws CommandException {
      unit = unit.toLowerCase().trim();
      StringBuilder res = new StringBuilder();

      for (Object v : type.getEnumConstants()) {
         if (v.toString().toLowerCase().equals(unit)) {
            return v;
         }

         res.append(type);
         res.append("|");
      }

      res.delete(res.length() - 1, res.length());
      throw new CommandException("Invalid argument found: " + unit + ". Accepted values are: " + res);
   }

   @Override
   public boolean isArray() {
      return this.isArray;
   }

   private boolean isEnum() {
      return this.tType.isEnum();
   }

   public Arg<T> asArray() {
      this.isArray = true;
      return this;
   }

   @Override
   public String getHelpRepresentation() {
      if (this.hasDefault()) {
         return this.showDefault ? "<" + this.name + "=" + this.Default + ">" : "<" + this.name + ">";
      } else {
         return "[" + this.name + "]";
      }
   }

   private <V> boolean isInstanceOf(Class<V> vType, Class<?> t) {
      return vType == t || vType.isAssignableFrom(t);
   }

   private <V> boolean isInstancesOf(Class<V> vType, Class<?>... types) {
      for (Class<?> t : types) {
         if (this.isInstanceOf(vType, t)) {
            return true;
         }
      }

      return false;
   }

   private void parseErrorCheck(boolean good, Object value, String type) throws CommandException {
      if (!good) {
         throw new CommandException("Failed to parse the following argument into type " + type + ": " + value + ".");
      }
   }

   private <V> V parseUnitUtil(Class<V> vType, String unit, String[] unitPlusRemainder) throws CommandException {
      if (this.isEnum()) {
         return this.getConverted(vType, parseEnum(unit, vType));
      } else {
         if (this.isInstanceOf(vType, Float.class)) {
            try {
               return this.getConverted(vType, Float.parseFloat(unit));
            } catch (NumberFormatException var8) {
               this.parseErrorCheck(false, unit, "float");
            }
         }

         if (this.isInstanceOf(vType, Double.class)) {
            try {
               return this.getConverted(vType, Double.parseDouble(unit));
            } catch (NumberFormatException var7) {
               this.parseErrorCheck(false, unit, "double");
            }
         }

         if (this.isInstanceOf(vType, Integer.class)) {
            try {
               return this.getConverted(vType, Integer.parseInt(unit));
            } catch (NumberFormatException var6) {
               this.parseErrorCheck(false, unit, "int");
            }
         }

         if (this.isInstanceOf(vType, Long.class)) {
            try {
               return this.getConverted(vType, Long.parseLong(unit));
            } catch (NumberFormatException var5) {
               this.parseErrorCheck(false, unit, "long");
            }
         }

         if (this.isInstanceOf(vType, ItemList.class)) {
            return this.getConverted(vType, ItemList.parseRemainder(String.join(" ", unitPlusRemainder)));
         } else if (this.isInstanceOf(vType, GotoTarget.class)) {
            return this.getConverted(vType, GotoTarget.parseRemainder(String.join(" ", unitPlusRemainder)));
         } else if (this.isInstanceOf(vType, String.class)) {
            if (unit.length() >= 2 && unit.charAt(0) == '"' && unit.charAt(unit.length() - 1) == '"') {
               unit = unit.substring(1, unit.length() - 1);
            }

            return this.getConverted(vType, unit);
         } else {
            throw new CommandException(
               "Arguments are not programmed to parse the following type: " + vType + ". This is either not implemented intentionally or by accident somehow."
            );
         }
      }
   }

   @Override
   public Object parseUnit(String unit, String[] unitPlusRemainder) throws CommandException {
      return this.parseUnitUtil(this.tType, unit, unitPlusRemainder);
   }

   public boolean checkValidUnit(String arg, StringBuilder errorMsg) {
      errorMsg.delete(0, errorMsg.length());
      return true;
   }

   @Override
   public <V> V getDefault(Class<V> vType) {
      return this.getConverted(vType, this.Default);
   }

   @Override
   public boolean isArbitrarilyLong() {
      return this.isInstanceOf(this.tType, ItemList.class) || this.isInstanceOf(this.tType, GotoTarget.class);
   }
}
