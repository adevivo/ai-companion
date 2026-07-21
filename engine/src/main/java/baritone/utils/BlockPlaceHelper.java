package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.entity.IInventoryProvider;
import baritone.api.utils.IEntityContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BlockPlaceHelper {
   private final IEntityContext ctx;
   private int rightClickTimer;

   BlockPlaceHelper(IEntityContext playerContext) {
      this.ctx = playerContext;
   }

   public void tick(boolean rightClickRequested) {
      if (this.rightClickTimer > 0) {
         this.rightClickTimer--;
      } else {
         HitResult mouseOver = this.ctx.objectMouseOver();
         boolean isRowingBoat = this.ctx.entity().getVehicle() != null && this.ctx.entity().getVehicle() instanceof Boat;
         if (rightClickRequested && this.ctx.entity() instanceof IInventoryProvider && !isRowingBoat && mouseOver != null && mouseOver.getType() == Type.BLOCK) {
            this.rightClickTimer = BaritoneAPI.getGlobalSettings().rightClickSpeed.get();
            LivingEntity player = this.ctx.entity();

            for (InteractionHand hand : InteractionHand.values()) {
               InteractionResult actionResult = this.ctx.playerController().processRightClickBlock(player, this.ctx.world(), hand, (BlockHitResult)mouseOver);
               if (actionResult.consumesAction()) {
                  if (actionResult.shouldSwing()) {
                     player.swing(hand);
                  }

                  return;
               }

               if (!player.getItemInHand(hand).isEmpty() && this.ctx.playerController().processRightClick(player, this.ctx.world(), hand).consumesAction()) {
                  return;
               }
            }
         }
      }
   }
}
