package adris.altoclef.control;

import adris.altoclef.AltoClefController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class PlayerExtraController {
   private final AltoClefController mod;

   public PlayerExtraController(AltoClefController mod) {
      this.mod = mod;
   }

   /**
    * Ask the movement layer what this companion is mining, rather than tracking it here.
    *
    * <p>This used to be a field fed by a {@code BlockBreakingEvent}/{@code BlockBreakingCancelEvent}
    * pair, and it was wrong twice over. The events were published from a mixin on
    * {@code MultiPlayerGameMode} — the <em>human client's</em> mining — which a server-side companion
    * never goes through, so the position belonged to the owner, not the companion. And the cancel
    * event fired exactly once per game process (its guard decremented a counter that started at zero
    * and was never reset), so after the owner's first block break the state never cleared again.
    *
    * <p>The combination left {@link #isBreakingBlock()} stuck true for the rest of the session, which
    * ran {@code PlayerInteractionFixChain}'s auto-tool swap every tick against a stale position — 822
    * equip swaps in an eight-minute session, fighting whatever the running task had in hand.
    */
   public BlockPos getBreakingBlockPos() {
      return this.mod.getBaritone().getInputOverrideHandler().getBreakingBlockPos();
   }

   public boolean isBreakingBlock() {
      return this.getBreakingBlockPos() != null;
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
