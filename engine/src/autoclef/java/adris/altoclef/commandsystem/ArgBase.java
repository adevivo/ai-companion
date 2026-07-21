package adris.altoclef.commandsystem;

import java.lang.reflect.ParameterizedType;

public abstract class ArgBase {
   protected int minArgCountToUseDefault;
   protected boolean hasDefault;

   protected <V> V getConverted(Class<V> vType, Object ob) {
      try {
         return (V)ob;
      } catch (Exception var4) {
         throw new IllegalArgumentException(
            "Tried to convert the following object to type {typeof(V)} and failed: {ob}. This is probably an internal problem, contact the dev!"
         );
      }
   }

   public <V> V parseUnit(String unit, String[] unitPlusRemainder) throws CommandException {
      Class<V> vType = (Class<V>)((ParameterizedType)this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
      return this.getConverted(vType, this.parseUnit(unit, unitPlusRemainder));
   }

   public abstract <V> V getDefault(Class<V> var1);

   public abstract String getHelpRepresentation();

   public int getMinArgCountToUseDefault() {
      return this.minArgCountToUseDefault;
   }

   public boolean hasDefault() {
      return this.hasDefault;
   }

   public boolean isArray() {
      return false;
   }

   public boolean isArbitrarilyLong() {
      return false;
   }
}
