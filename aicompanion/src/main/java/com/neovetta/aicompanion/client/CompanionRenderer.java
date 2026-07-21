package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.CompanionConfig;
import com.neovetta.aicompanion.entity.CompanionEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/**
 * Draws the companion with the vanilla player model. The held-item feature layer is required for
 * tools/weapons to show in the hands (a bare {@link LivingEntityRenderer} renders none). The skin comes
 * from config: a PNG dropped into {@code config/aicompanion/skins/} (see {@link CompanionSkin}), falling
 * back to the default Steve texture. The slim (Alex) arm model is selected from {@code companion.skin.slim}.
 */
public class CompanionRenderer extends LivingEntityRenderer<CompanionEntity, PlayerEntityModel<CompanionEntity>> {

    private static final Identifier DEFAULT_TEXTURE =
            new Identifier("minecraft", "textures/entity/player/wide/steve.png");

    public CompanionRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(
                        ctx.getPart(CompanionConfig.skinSlim() ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER),
                        CompanionConfig.skinSlim()),
                0.5f);
        // Render whatever is in the main hand / offhand (axe, sword, etc.).
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(CompanionEntity entity) {
        return CompanionSkin.textureOrDefault(DEFAULT_TEXTURE);
    }
}
