package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
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
        implements ExtendedScreenHandlerFactory<Integer> {

    /**
     * The opening payload is typed now rather than written by hand into a buffer: the factory
     * returns the value and the menu type owns the codec that puts it on the wire. Both halves have
     * to agree on that codec — see {@link CompanionScreens#TYPE}.
     */
    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return this.companion.getId();
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
