package baritone.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

public final class PathRenderer {
   static EntityRenderDispatcher renderManager = Minecraft.getInstance().getEntityRenderDispatcher();

   private PathRenderer() {
   }

   public static double posX() {
      return renderManager.camera.getPosition().x;
   }

   public static double posY() {
      return renderManager.camera.getPosition().y;
   }

   public static double posZ() {
      return renderManager.camera.getPosition().z;
   }
}
