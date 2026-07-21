package baritone.behavior;

import baritone.Baritone;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityInventory;
import baritone.utils.ToolSet;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InventoryBehavior extends Behavior {
   public InventoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onTickServer() {
      if (this.baritone.settings().allowInventory.get()) {
         if (this.ctx.entity() instanceof IInventoryProvider player) {
            if (this.firstValidThrowaway(player.getLivingInventory()) >= 9) {
               this.swapWithHotBar(this.firstValidThrowaway(player.getLivingInventory()), 8, player.getLivingInventory());
            }

            int pick = this.bestToolAgainst(Blocks.STONE, PickaxeItem.class);
            if (pick >= 9) {
               for (int i = 0; i < 9; i++) {
                  if (player.getLivingInventory().getItem(i).getItem() != Items.BUCKET) {
                     this.swapWithHotBar(pick, i, player.getLivingInventory());
                     break;
                  }
               }
            }
         }
      }
   }

   public void attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar, LivingEntityInventory inventory) {
      OptionalInt destination = this.getTempHotbarSlot(disallowedHotbar);
      if (destination.isPresent()) {
         this.swapWithHotBar(inMainInvy, destination.getAsInt(), inventory);
      }
   }

   public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
      LivingEntityInventory inventory = this.ctx.inventory();
      if (inventory == null) {
         return OptionalInt.empty();
      } else {
         ArrayList<Integer> candidates = new ArrayList<>();

         for (int i = 1; i < 8; i++) {
            if (((ItemStack)inventory.main.get(i)).isEmpty() && !disallowedHotbar.test(i)) {
               candidates.add(i);
            }
         }

         if (candidates.isEmpty()) {
            for (int ix = 1; ix < 8; ix++) {
               if (!disallowedHotbar.test(ix)) {
                  candidates.add(ix);
               }
            }
         }

         return candidates.isEmpty() ? OptionalInt.empty() : OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
      }
   }

   private void swapWithHotBar(int inInventory, int inHotbar, LivingEntityInventory inventory) {
      ItemStack h = inventory.getItem(inHotbar);
      inventory.setItem(inHotbar, inventory.getItem(inInventory));
      inventory.setItem(inInventory, h);
   }

   private int firstValidThrowaway(LivingEntityInventory inventory) {
      NonNullList<ItemStack> invy = inventory.main;

      for (int i = 0; i < invy.size(); i++) {
         if (this.baritone.settings().acceptableThrowawayItems.get().contains(((ItemStack)invy.get(i)).getItem())) {
            return i;
         }
      }

      return -1;
   }

   private int bestToolAgainst(Block against, Class<? extends TieredItem> cla$$) {
      NonNullList<ItemStack> invy = this.ctx.inventory().main;
      int bestInd = -1;
      double bestSpeed = -1.0;

      for (int i = 0; i < invy.size(); i++) {
         ItemStack stack = (ItemStack)invy.get(i);
         if (!stack.isEmpty()
            && (!this.baritone.settings().itemSaver.get() || stack.getDamageValue() < stack.getMaxDamage() || stack.getMaxDamage() <= 1)
            && cla$$.isInstance(stack.getItem())) {
            double speed = ToolSet.calculateSpeedVsBlock(stack, against.defaultBlockState());
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestInd = i;
            }
         }
      }

      return bestInd;
   }

   public boolean hasGenericThrowaway() {
      return this.throwaway(false, stack -> this.baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem()));
   }

   public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
      BlockState maybe = this.baritone.getBuilderProcess().placeAt(x, y, z, this.baritone.bsi.get0(x, y, z));
      if (maybe != null
         && this.throwaway(
            select,
            stack -> stack.getItem() instanceof BlockItem
               && maybe.equals(
                  ((BlockItem)stack.getItem())
                     .getBlock()
                     .getStateForPlacement(
                        new BlockPlaceContext(
                           new UseOnContext(
                              this.ctx.world(),
                              null,
                              InteractionHand.MAIN_HAND,
                              stack,
                              new BlockHitResult(
                                 new Vec3(this.ctx.entity().getX(), this.ctx.entity().getY(), this.ctx.entity().getZ()),
                                 Direction.UP,
                                 this.ctx.feetPos(),
                                 false
                              )
                           ) {
                              public boolean isSecondaryUseActive() {
                                 return false;
                              }
                           }
                        )
                     )
               )
         )) {
         return true;
      } else {
         return maybe != null
               && this.throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock().equals(maybe.getBlock()))
            ? true
            : this.throwaway(select, stack -> this.baritone.settings().acceptableThrowawayItems.get().contains(stack.getItem()));
      }
   }

   public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
      if (!(this.ctx.entity() instanceof IInventoryProvider p)) {
         return false;
      } else {
         NonNullList var7 = p.getLivingInventory().main;

         for (int i = 0; i < 9; i++) {
            ItemStack item = (ItemStack)var7.get(i);
            if (desired.test(item)) {
               if (select) {
                  p.getLivingInventory().selectedSlot = i;
               }

               return true;
            }
         }

         if (desired.test((ItemStack)p.getLivingInventory().offHand.get(0))) {
            for (int ix = 0; ix < 9; ix++) {
               ItemStack item = (ItemStack)var7.get(ix);
               if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
                  if (select) {
                     p.getLivingInventory().selectedSlot = ix;
                  }

                  return true;
               }
            }
         }

         return false;
      }
   }

   public static int getSlotWithStack(LivingEntityInventory inv, TagKey<Item> tag) {
      for (int i = 0; i < inv.main.size(); i++) {
         if (!((ItemStack)inv.main.get(i)).isEmpty() && ((ItemStack)inv.main.get(i)).is(tag)) {
            return i;
         }
      }

      return -1;
   }
}
