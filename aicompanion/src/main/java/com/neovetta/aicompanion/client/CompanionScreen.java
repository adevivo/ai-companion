package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.screen.CompanionScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The companion inventory window.
 *
 * <p>Armour column and offhand on the left, the companion's 36 storage slots to the right of them
 * (bottom row being the hotbar it actually draws from), your own inventory below. Layout is fixed in
 * {@link CompanionScreenHandler}; this only draws the background behind it.
 */
public class CompanionScreen extends AbstractContainerScreen<CompanionScreenHandler> {

    private static final Identifier TEXTURE =
            AiCompanion.id("textures/gui/container/companion.png");

    /** Matches the generated texture and the slot coordinates in the handler. */
    private static final int WIDTH = 176;
    private static final int HEIGHT = 216;

    public CompanionScreen(CompanionScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        // In the gap between the companion's hotbar row (which ends at y=114) and the player's own
        // rows, which start at y=130. Vanilla's 12px above the first row.
        this.inventoryLabelY = 118;
    }

    @Override
    protected void drawBackground(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        ctx.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
    }
}
