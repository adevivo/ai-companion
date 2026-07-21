package baritone.api;

import java.util.Calendar;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class BaritoneAPI {
   private static final IBaritoneProvider provider;

   public static IBaritoneProvider getProvider() {
      return provider;
   }

   public static Settings getGlobalSettings() {
      return getProvider().getGlobalSettings();
   }

   public static Component getPrefix() {
      Calendar now = Calendar.getInstance();
      boolean xd = now.get(2) == 3 && now.get(5) <= 3;
      MutableComponent baritone = Component.literal(xd ? "Automatoe" : (getGlobalSettings().shortBaritonePrefix.get() ? "A" : "Automatone"));
      baritone.setStyle(baritone.getStyle().applyFormat(ChatFormatting.GREEN));
      MutableComponent prefix = Component.literal("");
      prefix.setStyle(baritone.getStyle().applyFormat(ChatFormatting.DARK_GREEN));
      prefix.append("[");
      prefix.append(baritone);
      prefix.append("]");
      return prefix;
   }

   static {
      try {
         provider = (IBaritoneProvider)Class.forName("baritone.BaritoneProvider").getField("INSTANCE").get(null);
      } catch (ReflectiveOperationException var1) {
         throw new RuntimeException(var1);
      }
   }
}
