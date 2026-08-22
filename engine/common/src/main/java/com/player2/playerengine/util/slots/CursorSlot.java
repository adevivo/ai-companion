package com.player2.playerengine.util.slots;

public final class CursorSlot extends Slot {
   public static final CursorSlot SLOT = new CursorSlot();

   private CursorSlot() {
      super(null, -1);
   }

   @Override
   protected String getName() {
      return "Cursor Slot";
   }
}
