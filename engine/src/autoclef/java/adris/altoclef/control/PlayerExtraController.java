package adris.altoclef.control;

import adris.altoclef.AltoClefController;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.BlockBreakingCancelEvent;
import adris.altoclef.eventbus.events.BlockBreakingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class PlayerExtraController {
   private final AltoClefController mod;
   private BlockPos blockBreakPos;

   public PlayerExtraController(AltoClefController mod) {
      this.mod = mod;
      EventBus.subscribe(BlockBreakingEvent.class, evt -> this.onBlockBreak(evt.blockPos));
      EventBus.subscribe(BlockBreakingCancelEvent.class, evt -> this.onBlockStopBreaking());
   }

   private void onBlockBreak(BlockPos pos) {
      this.blockBreakPos = pos;
   }

   private void onBlockStopBreaking() {
      this.blockBreakPos = null;
   }

   public BlockPos getBreakingBlockPos() {
      return this.blockBreakPos;
   }

   public boolean isBreakingBlock() {
      return this.blockBreakPos != null;
   }

   public boolean inRange(Entity entity) {
      return this.mod.getPlayer().closerThan(entity, this.mod.getModSettings().getEntityReachRange());
   }

   public void attack(Entity entity) {
      if (this.inRange(entity)) {
         this.mod.getPlayer().doHurtTarget(entity);
         this.mod.getPlayer().swing(InteractionHand.MAIN_HAND);
      }
   }
}
