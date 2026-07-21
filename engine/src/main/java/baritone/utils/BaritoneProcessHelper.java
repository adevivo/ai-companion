package baritone.utils;

import baritone.Baritone;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.IEntityContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public abstract class BaritoneProcessHelper implements IBaritoneProcess {
   protected final Baritone baritone;
   protected final IEntityContext ctx;

   public BaritoneProcessHelper(Baritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getEntityContext();
   }

   @Override
   public boolean isTemporary() {
      return false;
   }

   public void logDirect(Component... components) {
      this.baritone.logDirect(components);
   }

   public void logDirect(String message, ChatFormatting color) {
      this.baritone.logDirect(message, color);
   }

   public void logDirect(String message) {
      this.baritone.logDirect(message);
   }
}
