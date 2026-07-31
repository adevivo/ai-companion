package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeVarInt(this.companion.getId());
    }

    @Override
    public Text getDisplayName() {
        return this.companion.getCustomName() != null
                ? this.companion.getCustomName()
                : Text.literal(this.companion.displayName());
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CompanionScreenHandler(syncId, playerInventory, this.companion);
    }
}
