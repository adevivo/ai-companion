package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.entity.CompanionEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Draws the companion with the vanilla player model. The held-item feature layer is required for
 * tools/weapons to show in the hands (a bare {@link LivingEntityRenderer} renders none). The skin
 * comes from that companion's own roster entry: a PNG dropped into
 * {@code config/aicompanion/skins/} (see {@link CompanionSkin}), falling back to the default Steve
 * texture.
 */
public class CompanionRenderer extends LivingEntityRenderer<CompanionEntity, PlayerEntityModel<CompanionEntity>> {

    private static final Identifier DEFAULT_TEXTURE =
            new Identifier("minecraft", "textures/entity/player/wide/steve.png");

    /**
     * Both arm shapes, chosen per entity in {@link #render}.
     *
     * <p>The arm model is baked into the renderer at construction and a renderer is registered once
     * per entity type, so a roster where one companion is slim and another is wide has nowhere else
     * to express that. Swapping {@code this.model} before the superclass draws is the standard way
     * round it: rendering is single-threaded, and every path that reads the field runs inside the
     * {@code super.render} call below.
     */
    private final PlayerEntityModel<CompanionEntity> wideModel;
    private final PlayerEntityModel<CompanionEntity> slimModel;

    public CompanionRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        // Render whatever is in the main hand / offhand (axe, sword, etc.).
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public void render(CompanionEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        this.model = entity.isSkinSlim() ? this.slimModel : this.wideModel;
        super.render(entity, yaw, tickDelta, matrices, vertices, light);
    }

    /**
     * Skin precedence: local PNG, then borrowed Mojang skin, then default Steve.
     *
     * <p>The file wins because it is the explicit override — someone who dropped a PNG in and named it
     * meant that face. The username is the convenient default, not the authoritative one.
     */
    @Override
    public Identifier getTexture(CompanionEntity entity) {
        String file = entity.getSkinFile();
        if (!file.isBlank()) {
            return CompanionSkin.textureOrDefault(file, DEFAULT_TEXTURE);
        }
        return CompanionSkin.textureFromProfile(entity.getSkinTexture(), DEFAULT_TEXTURE);
    }
}
