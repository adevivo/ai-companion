package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.screen.CompanionScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The companion inventory window.
 *
 * <p>Armour column and offhand on the left, the companion's 36 storage slots to the right of them
 * (bottom row being the hotbar it actually draws from), your own inventory below. Layout is fixed in
 * {@link CompanionScreenHandler}; this only draws the background behind it.
 */
public class CompanionScreen extends HandledScreen<CompanionScreenHandler> {

    private static final Identifier TEXTURE =
            AiCompanion.id("textures/gui/container/companion.png");

    /** Matches the generated texture and the slot coordinates in the handler. */
    private static final int WIDTH = 176;
    private static final int HEIGHT = 216;

    public CompanionScreen(CompanionScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
        // In the gap between the companion's hotbar row (which ends at y=114) and the player's own
        // rows, which start at y=130. Vanilla's 12px above the first row.
        this.playerInventoryTitleY = 118;
    }

    @Override
    protected void drawBackground(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        ctx.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}
