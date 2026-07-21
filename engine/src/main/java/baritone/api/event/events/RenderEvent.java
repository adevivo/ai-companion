package baritone.api.event.events;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

public final class RenderEvent {
   private final float partialTicks;
   private final Matrix4f projectionMatrix;
   private final PoseStack modelViewStack;

   public RenderEvent(float partialTicks, PoseStack modelViewStack, Matrix4f projectionMatrix) {
      this.partialTicks = partialTicks;
      this.modelViewStack = modelViewStack;
      this.projectionMatrix = projectionMatrix;
   }

   public final float getPartialTicks() {
      return this.partialTicks;
   }

   public PoseStack getModelViewStack() {
      return this.modelViewStack;
   }

   public Matrix4f getProjectionMatrix() {
      return this.projectionMatrix;
   }
}
