package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

/** Registration for the companion inventory screen. Called from {@link AiCompanion#onInitialize()}. */
public final class CompanionScreens {

    private CompanionScreens() {}

    /**
     * The companion inventory screen handler type.
     *
     * <p>Extended rather than plain because the client has to be told <em>which</em> companion it is
     * looking at — the handler is built independently on both sides, and with more than one companion
     * out "the nearest one" is not an answer. The entity id travels in the opening packet.
     */
    public static final ScreenHandlerType<CompanionScreenHandler> TYPE =
            new ExtendedScreenHandlerType<>((syncId, playerInventory, buf) -> {
                Entity entity = playerInventory.player.getWorld().getEntityById(buf.readVarInt());
                if (!(entity instanceof CompanionEntity companion)) {
                    return null; // the companion left the client's view between opening and reading
                }
                return new CompanionScreenHandler(syncId, playerInventory, companion);
            });

    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER_TYPE, AiCompanion.id("companion_inventory"), TYPE);
    }
}
