package baritone.client;

import baritone.entity.CustomFishingBobberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CustomFishingBobberRenderer extends EntityRenderer<CustomFishingBobberEntity> {
   private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/fishing_hook.png");
   private static final RenderType LAYER = RenderType.entityCutout(TEXTURE);
   private static final double BOBBING_VIEW_SCALE = 960.0;

   public CustomFishingBobberRenderer(Context context) {
      super(context);
   }

   public void render(CustomFishingBobberEntity fishingBobberEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
      LivingEntity playerEntity = fishingBobberEntity.getPlayerOwner();
      if (playerEntity != null) {
         matrixStack.pushPose();
         matrixStack.pushPose();
         matrixStack.scale(0.5F, 0.5F, 0.5F);
         matrixStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
         matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F));
         Pose entry = matrixStack.last();
         Matrix4f matrix4f = entry.pose();
         Matrix3f matrix3f = entry.normal();
         VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LAYER);
         vertex(vertexConsumer, matrix4f, matrix3f, i, 0.0F, 0, 0, 1);
         vertex(vertexConsumer, matrix4f, matrix3f, i, 1.0F, 0, 1, 1);
         vertex(vertexConsumer, matrix4f, matrix3f, i, 1.0F, 1, 1, 0);
         vertex(vertexConsumer, matrix4f, matrix3f, i, 0.0F, 1, 0, 0);
         matrixStack.popPose();
         int j = playerEntity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
         ItemStack itemStack = playerEntity.getMainHandItem();
         if (!itemStack.is(Items.FISHING_ROD)) {
            j = -j;
         }

         float h = playerEntity.getAttackAnim(g);
         float k = Mth.sin(Mth.sqrt(h) * (float) Math.PI);
         float l = Mth.lerp(g, playerEntity.yBodyRotO, playerEntity.yBodyRot) * (float) (Math.PI / 180.0);
         double d = Mth.sin(l);
         double e = Mth.cos(l);
         double m = j * 0.35;
         double n = 0.8;
         double o;
         double p;
         double q;
         float r;
         if ((this.entityRenderDispatcher.options == null || this.entityRenderDispatcher.options.getCameraType().isFirstPerson())
            && playerEntity == Minecraft.getInstance().player) {
            double s = 960.0 / ((Integer)this.entityRenderDispatcher.options.fov().get()).intValue();
            Vec3 vec3d = this.entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(j * 0.525F, -0.1F);
            vec3d = vec3d.scale(s);
            vec3d = vec3d.yRot(k * 0.5F);
            vec3d = vec3d.xRot(-k * 0.7F);
            o = Mth.lerp(g, playerEntity.xo, playerEntity.getX()) + vec3d.x;
            p = Mth.lerp(g, playerEntity.yo, playerEntity.getY()) + vec3d.y;
            q = Mth.lerp(g, playerEntity.zo, playerEntity.getZ()) + vec3d.z;
            r = playerEntity.getEyeHeight();
         } else {
            o = Mth.lerp(g, playerEntity.xo, playerEntity.getX()) - e * m - d * 0.8;
            p = playerEntity.yo + playerEntity.getEyeHeight() + (playerEntity.getY() - playerEntity.yo) * g - 0.45;
            q = Mth.lerp(g, playerEntity.zo, playerEntity.getZ()) - d * m + e * 0.8;
            r = playerEntity.isCrouching() ? -0.1875F : 0.0F;
         }

         double s = Mth.lerp(g, fishingBobberEntity.xo, fishingBobberEntity.getX());
         double t = Mth.lerp(g, fishingBobberEntity.yo, fishingBobberEntity.getY()) + 0.25;
         double u = Mth.lerp(g, fishingBobberEntity.zo, fishingBobberEntity.getZ());
         float v = (float)(o - s);
         float w = (float)(p - t) + r;
         float x = (float)(q - u);
         VertexConsumer vertexConsumer2 = vertexConsumerProvider.getBuffer(RenderType.lineStrip());
         Pose entry2 = matrixStack.last();
         int y = 16;

         for (int z = 0; z <= 16; z++) {
            drawArcSection(v, w, x, vertexConsumer2, entry2, percentage(z, 16), percentage(z + 1, 16));
         }

         matrixStack.popPose();
         super.render(fishingBobberEntity, f, g, matrixStack, vertexConsumerProvider, i);
      }
   }

   private static float percentage(int value, int max) {
      return (float)value / max;
   }

   private static void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, int light, float x, int y, int u, int v) {
      buffer.vertex(matrix, x - 0.5F, y - 0.5F, 0.0F)
         .color(255, 255, 255, 255)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
         .endVertex();
   }

   private static void drawArcSection(float x, float y, float z, VertexConsumer buffer, Pose normal, float startPercent, float endPercent) {
      float f = x * startPercent;
      float g = y * (startPercent * startPercent + startPercent) * 0.5F + 0.25F;
      float h = z * startPercent;
      float i = x * endPercent - f;
      float j = y * (endPercent * endPercent + endPercent) * 0.5F + 0.25F - g;
      float k = z * endPercent - h;
      float l = Mth.sqrt(i * i + j * j + k * k);
      i /= l;
      j /= l;
      k /= l;
      buffer.vertex(normal.pose(), f, g, h).color(0, 0, 0, 255).normal(normal.normal(), i, j, k).endVertex();
   }

   public ResourceLocation getTextureLocation(CustomFishingBobberEntity fishingBobberEntity) {
      return TEXTURE;
   }
}
