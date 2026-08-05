package adris.altoclef.util.helpers;

import adris.altoclef.AltoClefController;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * Raising and lowering a shield.
 *
 * <p>Both {@code MobDefenseChain} and {@code KillAura} decide independently when a shield should be
 * up, and both used to do it by holding {@code Input.CLICK_RIGHT}. That never worked. A held
 * right-click reaches exactly one consumer — {@code BlockPlaceHelper} — which does nothing unless the
 * crosshair is on a <em>block</em>, and even then routes through
 * {@code LivingEntityInteractionManager.interactItem}, which calls {@code stack.use(world, null, hand)}
 * with a null {@code Player} and throws into a bare {@code catch (Exception)}. So the companion carried
 * shields it could not raise, silently, and both chains believed they were blocking. This is the same
 * defect that kept the companion from eating, in a second place.
 *
 * <p>Driving {@link LivingEntity#startUsingItem} directly is what a shield actually needs: everything
 * downstream of it — {@code isBlocking}, {@code isDamageSourceBlocked}, the damage cancellation in
 * {@code LivingEntity.hurt} — is defined on {@code LivingEntity} and works for a companion unchanged.
 */
public final class ShieldHelper {

   private ShieldHelper() {
   }

   /**
    * Put the shield up, if there is one in the offhand and nothing better to do with the hands.
    *
    * @return whether the companion is now blocking
    */
   public static boolean raiseShield(AltoClefController mod) {
      LivingEntity entity = mod.getEntity();
      if (entity == null || !entity.getItemInHand(InteractionHand.OFF_HAND).is(Items.SHIELD)) {
         return false;
      }

      // Eating wins. Both are "using an item" and only one can be in progress, so raising the shield
      // over a meal would quietly starve a companion that is under enough pressure to want the shield
      // up — which is precisely when it is also taking the damage that makes it need to eat.
      if (mod.getFoodChain() != null && mod.getFoodChain().isTryingToEat()) {
         return false;
      }

      if (entity.isUsingItem()) {
         // Already blocking is success; already eating (or drinking) is not something to interrupt.
         return entity.getUsedItemHand() == InteractionHand.OFF_HAND;
      }

      entity.startUsingItem(InteractionHand.OFF_HAND);
      return true;
   }

   /** Drop the shield. Safe to call when it was never up, and never interrupts a meal. */
   public static void lowerShield(AltoClefController mod) {
      LivingEntity entity = mod.getEntity();
      if (entity != null && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
         entity.stopUsingItem();
      }
   }

   /** Whether the shield is up far enough to actually stop a hit. Vanilla wants five ticks. */
   public static boolean isBlocking(AltoClefController mod) {
      LivingEntity entity = mod.getEntity();
      return entity != null && entity.isBlocking();
   }
}
