package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.entity.CompanionEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * The companion's inventory, side by side with your own.
 *
 * <p>Lays out all 41 of its slots — 36 storage (of which the first 9 are the hotbar it actually draws
 * from), 4 armour, 1 offhand — over the player's own 36. The backing
 * {@code LivingEntityInventory} is a real {@link Inventory} with the vanilla player layout, so the
 * slots map straight onto it with no adapter: indices 0-35 storage, 36-39 armour, 40 offhand.
 *
 * <p>Everything here runs on the server thread, which is also where the AI drives the same inventory
 * from the entity tick, so the two cannot interleave mid-transfer. Taking the sword out of a
 * companion's hand while it is fighting will confuse it — that is the owner's business, not a race.
 */
public class CompanionScreenHandler extends ScreenHandler {

    /** How far you can walk before an open screen closes itself. Vanilla's container reach. */
    private static final double MAX_REACH_SQUARED = 64.0D;

    /** Companion inventory indices, matching {@code LivingEntityInventory}'s combined layout. */
    private static final int STORAGE_SLOTS = 36;
    private static final int ARMOR_START = 36;
    private static final int OFFHAND_SLOT = 40;
    private static final int COMPANION_SLOTS = 41;

    /** Armour slots top-to-bottom, so index 0 of the column is the helmet. */
    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Inventory companionInventory;
    private final CompanionEntity companion;

    public CompanionScreenHandler(int syncId, PlayerInventory playerInventory, CompanionEntity companion) {
        super(CompanionScreens.TYPE, syncId);
        this.companion = companion;
        this.companionInventory = companion.getLivingInventory();
        this.companionInventory.onOpen(playerInventory.player);

        // Armour across the top, helmet first. A 9-wide storage grid needs the full width of the
        // panel, so armour cannot sit in a column beside it as it does on the player's own screen.
        // The armour slot id in LivingEntityInventory counts from the feet up (vanilla's
        // getEntitySlotId), so the index is derived per piece rather than from the loop counter.
        for (int col = 0; col < ARMOR_ORDER.length; col++) {
            final EquipmentSlot equipment = ARMOR_ORDER[col];
            int index = ARMOR_START + equipment.getEntitySlotId();
            this.addSlot(new ArmorSlot(this.companionInventory, index, 8 + col * 18, 18, equipment));
        }
        // Offhand, set apart from the armour so it does not read as a fifth piece.
        this.addSlot(new OffhandSlot(this.companionInventory, OFFHAND_SLOT, 98, 18));

        // Storage rows 9-35, then the hotbar row 0-8 beneath them after a gap — the same shape as a
        // player's own screen, so the row it actually holds items from reads as the hotbar it is.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(this.companionInventory, 9 + col + row * 9, 8 + col * 18, 40 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(this.companionInventory, col, 8 + col * 18, 98));
        }

        // The player's own inventory, far enough below the companion's hotbar row to leave room for
        // the "Inventory" label between the two.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, 9 + col + row * 9, 8 + col * 18, 130 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 190));
        }
    }

    /** Armour slot: one item, and only armour that actually belongs in this piece's place. */
    private static class ArmorSlot extends Slot {
        private final EquipmentSlot equipment;

        ArmorSlot(Inventory inventory, int index, int x, int y, EquipmentSlot equipment) {
            super(inventory, index, x, y);
            this.equipment = equipment;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return LivingEntity.getPreferredEquipmentSlot(stack) == this.equipment;
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            // Cursed armour cannot be taken off a player; the same should hold for a companion, or
            // the curse is trivially undone by handing the piece over and taking it back.
            ItemStack stack = this.getStack();
            return (stack.isEmpty() || player.isCreative() || !EnchantmentHelper.hasBindingCurse(stack))
                    && super.canTakeItems(player);
        }
    }

    /** Offhand slot: one stack of anything, like a player's. Shields are the point of it. */
    private static class OffhandSlot extends Slot {
        OffhandSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
    }

    /**
     * Shift-click transfer.
     *
     * <p>Written out rather than borrowed because the two halves are not symmetrical: coming from the
     * player, armour and shields should land where they are worn rather than in the first free
     * storage slot, which is the difference between handing over a helmet and having to place it by
     * hand afterwards.
     *
     * <p>Returning {@link ItemStack#EMPTY} whenever nothing moved is what terminates the caller's
     * loop; getting that wrong is how shift-click hangs a server.
     */
    @Override
    public ItemStack quickTransfer(PlayerEntity player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getStack();
        ItemStack original = inSlot.copy();

        // Slot ranges in the order they were added above.
        final int companionEnd = COMPANION_SLOTS;                  // 0..40  companion
        final int playerEnd = COMPANION_SLOTS + STORAGE_SLOTS;     // 41..76 player

        if (index < companionEnd) {
            // Companion → player.
            if (!this.insertItem(inSlot, companionEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player → companion: try to wear it first, then the storage rows, then the hotbar row.
            EquipmentSlot preferred = LivingEntity.getPreferredEquipmentSlot(inSlot);
            int armorIndex = armorSlotIndexFor(preferred);
            boolean moved = false;
            if (armorIndex >= 0 && !this.slots.get(armorIndex).hasStack()) {
                moved = this.insertItem(inSlot, armorIndex, armorIndex + 1, false);
            }
            if (!moved && inSlot.isOf(Items.SHIELD) && !this.slots.get(4).hasStack()) {
                moved = this.insertItem(inSlot, 4, 5, false); // the offhand slot
            }
            if (!moved) {
                moved = this.insertItem(inSlot, 5, companionEnd, false); // storage, then hotbar row
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (inSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY; // nothing actually moved
        }
        slot.onTakeItem(player, inSlot);
        return original;
    }

    /** Index in {@link #slots} of the armour slot for {@code equipment}, or -1 if it is not armour. */
    private static int armorSlotIndexFor(EquipmentSlot equipment) {
        for (int i = 0; i < ARMOR_ORDER.length; i++) {
            if (ARMOR_ORDER[i] == equipment) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the screen may stay open: still the owner's companion, still alive, still in reach.
     *
     * <p>The reach check is the one that matters. A companion is not a chest — it walks off on its own
     * while the screen is open, and without this the owner would keep reaching into an inventory from
     * across the world, including into chunks that are no longer loaded.
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        if (!this.companion.isAlive() || this.companion.isRemoved()) {
            return false;
        }
        if (this.companion.getOwnerUuid() != null
                && !this.companion.getOwnerUuid().equals(player.getUuid())) {
            return false;
        }
        return player.squaredDistanceTo(this.companion) <= MAX_REACH_SQUARED
                && this.companionInventory.canPlayerUse(player);
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        // Armour and held items are read straight off this inventory (see
        // CompanionEntity#getEquippedStack), so anything just handed over takes effect on the next
        // tick with no extra plumbing — including the attribute modifiers of a weapon or a helmet.
        this.companionInventory.onClose(player);
    }

    /** The companion this screen belongs to — the client screen uses it for the title. */
    public CompanionEntity getCompanion() {
        return this.companion;
    }
}
