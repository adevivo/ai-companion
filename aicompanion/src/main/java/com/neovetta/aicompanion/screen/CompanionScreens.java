package com.neovetta.aicompanion.screen;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;

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
    public static final MenuType<CompanionScreenHandler> TYPE =
            new ExtendedScreenHandlerType<>((syncId, playerInventory, buf) -> {
                Entity entity = playerInventory.player.getWorld().getEntity(buf.readVarInt());
                if (!(entity instanceof CompanionEntity companion)) {
                    return null; // the companion left the client's view between opening and reading
                }
                return new CompanionScreenHandler(syncId, playerInventory, companion);
            });

    public static void register() {
        Registry.register(BuiltInRegistries.MENU, AiCompanion.id("companion_inventory"), TYPE);
    }
}
