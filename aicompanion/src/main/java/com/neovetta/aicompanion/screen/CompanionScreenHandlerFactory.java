package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * Opens {@link CompanionScreenHandler} for one specific companion.
 *
 * <p>Sends the companion's entity id with the opening packet so the client builds its half of the
 * screen against the same body. Without it the client would have to guess, and "the nearest
 * companion" stops being a safe guess the moment there are two.
 */
public record CompanionScreenHandlerFactory(CompanionEntity companion)
        implements ExtendedScreenHandlerFactory {

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        buf.writeVarInt(this.companion.getId());
    }

    @Override
    public Component getDisplayName() {
        return this.companion.getCustomName() != null
                ? this.companion.getCustomName()
                : Component.literal(this.companion.displayName());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new CompanionScreenHandler(syncId, playerInventory, this.companion);
    }
}
