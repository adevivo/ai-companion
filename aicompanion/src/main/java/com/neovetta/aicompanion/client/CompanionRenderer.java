package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.entity.CompanionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws the companion with the vanilla player model. The held-item feature layer is required for
 * tools/weapons to show in the hands (a bare {@link LivingEntityRenderer} renders none). The skin
 * comes from that companion's own roster entry: a PNG dropped into
 * {@code config/aicompanion/skins/} (see {@link CompanionSkin}), falling back to the default Steve
 * texture.
 *
 * <h2>Why the render state is {@code AvatarRenderState} exactly</h2>
 *
 * 1.21 split rendering into two phases: everything the renderer needs is copied out of the entity
 * into a render state, and drawing then reads only that state. The obvious move is a subclass of
 * {@link AvatarRenderState} carrying our texture — but {@link PlayerModel} is
 * {@code HumanoidModel<AvatarRenderState>}, and {@link ItemInHandLayer} demands
 * {@code EntityModel<S>} <em>invariantly</em>. A subclass therefore cannot be paired with the
 * player model and the held-item layer at the same time.
 *
 * <p>So the state is {@code AvatarRenderState} itself, and the per-companion skin travels in the
 * field it already has for exactly this: {@link AvatarRenderState#skin}. That carries the arm shape
 * too, which is the same question this renderer used to answer with two model fields.
 */
public class CompanionRenderer
        extends LivingEntityRenderer<CompanionEntity, AvatarRenderState, PlayerModel> {

    private static final Identifier DEFAULT_TEXTURE = DefaultPlayerSkin.getDefaultTexture();

    /**
     * A texture whose path is already complete.
     *
     * <p>{@link ClientAsset.ResourceTexture} rewrites what it is given into
     * {@code <namespace>:textures/<path>.png}, which is right for an asset shipped in a resource
     * pack and wrong for ours — {@link CompanionSkin} registers its images with the texture manager
     * under the identifier it hands back, and that identifier is the whole path.
     */
    private record ExactTexture(Identifier texturePath) implements ClientAsset.Texture {
        @Override
        public Identifier id() {
            return texturePath;
        }
    }

    /**
     * Both arm shapes, chosen per entity in {@link #submit}.
     *
     * <p>The arm model is baked into the renderer at construction and a renderer is registered once
     * per entity type, so a roster where one companion is slim and another is wide has nowhere else
     * to express that. Swapping {@code this.model} before the superclass draws is the standard way
     * round it: submission is single-threaded, and every path that reads the field runs inside the
     * {@code super.submit} call below.
     */
    private final PlayerModel wideModel;
    private final PlayerModel slimModel;

    public CompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        // Render whatever is in the main hand / offhand (axe, sword, etc.).
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    /**
     * Copy the companion into the render state.
     *
     * <p>{@code super} fills the {@link net.minecraft.client.renderer.entity.state.LivingEntityRenderState}
     * half; the humanoid half — arm poses, crouching, held items — is a static helper rather than
     * something inherited, because the renderer that normally provides it is bound to {@code Mob}
     * and a companion is a bare {@code LivingEntity}.
     */
    @Override
    public void extractRenderState(CompanionEntity entity, AvatarRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, this.itemModelResolver);

        // Skin precedence: local PNG, then borrowed Mojang skin, then default Steve. The file wins
        // because it is the explicit override — someone who dropped a PNG in and named it meant that
        // face. The username is the convenient default, not the authoritative one.
        String file = entity.getSkinFile();
        Identifier texture = !file.isBlank()
                ? CompanionSkin.textureOrDefault(file, DEFAULT_TEXTURE)
                : CompanionSkin.textureFromProfile(entity.getSkinTexture(), DEFAULT_TEXTURE);
        state.skin = new PlayerSkin(new ExactTexture(texture), null, null,
                entity.isSkinSlim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, false);

        // The model draws the outer "clothing" layer only when told to. A real player gets these
        // from their own skin-customisation options; a companion has nobody to ask, and with them
        // left false it renders with no hat layer, no jacket and no sleeves.
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
    }

    @Override
    public void submit(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        this.model = state.skin.model() == PlayerModelType.SLIM ? this.slimModel : this.wideModel;
        super.submit(state, pose, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState state) {
        return state.skin.body().texturePath();
    }
}
