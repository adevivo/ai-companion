package baritone.api.event.events;

import net.minecraft.core.BlockPos;

public final class BlockInteractEvent {
   private final BlockPos pos;
   private final BlockInteractEvent.Type type;

   public BlockInteractEvent(BlockPos pos, BlockInteractEvent.Type type) {
      this.pos = pos;
      this.type = type;
   }

   public final BlockPos getPos() {
      return this.pos;
   }

   public final BlockInteractEvent.Type getType() {
      return this.type;
   }

   public static enum Type {
      START_BREAK,
      USE;
   }
}
