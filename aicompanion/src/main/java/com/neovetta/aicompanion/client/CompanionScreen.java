package com.neovetta.aicompanion.client;

import net.minecraft.client.renderer.RenderPipelines;
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

    /** The PNG's own size. blit needs it to derive UVs; the panel above is a region of it. */
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;

    public CompanionScreen(CompanionScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        // In the gap between the companion's hotbar row (which ends at y=114) and the player's own
        // rows, which start at y=130. Vanilla's 12px above the first row.
        this.inventoryLabelY = 118;
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // blit now names the pipeline it draws through and takes the texture's own dimensions, so
        // it can work out UVs rather than assuming the old fixed 256x256 sheet.
        ctx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
    }
}
