package baritone.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.BetterBlockPos;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class GuiClick extends Screen {
   private final UUID callerUuid;
   private Matrix4f projectionViewMatrix;
   private BlockPos clickStart;
   private BlockPos currentMouseOver;

   public GuiClick(UUID callerUuid) {
      super(Component.literal("CLICK"));
      this.callerUuid = callerUuid;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
      Minecraft mc = Minecraft.getInstance();
      double mx = mc.mouseHandler.xpos();
      double my = mc.mouseHandler.ypos();
      my = mc.getWindow().getScreenHeight() - my;
      my *= (double)mc.getWindow().getHeight() / mc.getWindow().getScreenHeight();
      mx *= (double)mc.getWindow().getWidth() / mc.getWindow().getScreenWidth();
      Vec3 near = this.toWorld(mx, my, 0.0);
      Vec3 far = this.toWorld(mx, my, 1.0);
      if (near != null && far != null) {
         Vec3 viewerPos = new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
         Player player = Objects.requireNonNull(Minecraft.getInstance().player);
         BlockHitResult result = player.level().clip(new ClipContext(near.add(viewerPos), far.add(viewerPos), Block.OUTLINE, Fluid.NONE, player));
         if (result != null && result.getType() == Type.BLOCK) {
            this.currentMouseOver = result.getBlockPos();
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
      if (this.currentMouseOver != null) {
         Minecraft client = this.minecraft;

         assert client != null;

         assert client.player != null;

         assert client.level != null;

         if (mouseButton == 0) {
            if (this.clickStart != null && !this.clickStart.equals(this.currentMouseOver)) {
               client.player.connection.sendCommand(String.format("execute as %s run automatone sel clear", this.callerUuid));
               client.player
                  .connection
                  .sendCommand(
                     String.format(
                        "execute as %s run automatone sel 1 %d %d %d", this.callerUuid, this.clickStart.getX(), this.clickStart.getY(), this.clickStart.getZ()
                     )
                  );
               client.player
                  .connection
                  .sendCommand(
                     String.format(
                        "execute as %s run automatone sel 2 %d %d %d",
                        this.callerUuid,
                        this.currentMouseOver.getX(),
                        this.currentMouseOver.getY(),
                        this.currentMouseOver.getZ()
                     )
                  );
               MutableComponent component = Component.literal("").append(BaritoneAPI.getPrefix()).append(" Selection made! For usage: /automatone help sel");
               component.setStyle(
                  component.getStyle().applyFormat(ChatFormatting.WHITE).withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/automatone help sel"))
               );
               client.gui.getChat().addMessage(component);
            } else {
               client.player
                  .connection
                  .sendCommand(
                     String.format(
                        "execute as %s run automatone goto %d %d %d",
                        this.callerUuid,
                        this.currentMouseOver.getX(),
                        this.currentMouseOver.getY(),
                        this.currentMouseOver.getZ()
                     )
                  );
            }
         } else if (mouseButton == 1) {
            client.player
               .connection
               .sendCommand(
                  String.format(
                     "execute as %s run automatone goto %d %d %d",
                     this.callerUuid,
                     this.currentMouseOver.getX(),
                     this.currentMouseOver.getY() + 1,
                     this.currentMouseOver.getZ()
                  )
               );
         }
      }

      this.clickStart = null;
      return super.mouseReleased(mouseX, mouseY, mouseButton);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
      this.clickStart = this.currentMouseOver;
      return super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   public void onRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
      this.projectionViewMatrix = new Matrix4f(projectionMatrix);
      this.projectionViewMatrix.mul(modelViewStack.last().pose());
      this.projectionViewMatrix.invert();
      if (this.currentMouseOver != null) {
         Entity e = Minecraft.getInstance().getCameraEntity();
         Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();

         assert e != null;

         VertexConsumer vertexConsumer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
         LevelRenderer.renderLineBox(
            modelViewStack,
            vertexConsumer,
            new AABB(this.currentMouseOver).move(-c.getPosition().x, -c.getPosition().y, -c.getPosition().z).inflate(0.002),
            0.0F,
            1.0F,
            1.0F,
            1.0F
         );
         if (this.clickStart != null && !this.clickStart.equals(this.currentMouseOver)) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(770, 771, 1, 0);
            RenderSystem.lineWidth(BaritoneAPI.getGlobalSettings().pathRenderLineWidthPixels.get());
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            BetterBlockPos a = new BetterBlockPos(this.currentMouseOver);
            BetterBlockPos b = new BetterBlockPos(this.clickStart);
            LevelRenderer.renderLineBox(
               modelViewStack,
               vertexConsumer,
               new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x) + 1, Math.max(a.y, b.y) + 1, Math.max(a.z, b.z) + 1)
                  .move(-c.getPosition().x, -c.getPosition().y, -c.getPosition().z),
               1.0F,
               0.0F,
               0.0F,
               0.4F
            );
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
         }
      }
   }

   private Vec3 toWorld(double x, double y, double z) {
      if (this.projectionViewMatrix == null) {
         return null;
      } else {
         Window window = Minecraft.getInstance().getWindow();
         x /= window.getWidth();
         y /= window.getHeight();
         x = x * 2.0 - 1.0;
         y = y * 2.0 - 1.0;
         Vector4f pos = new Vector4f((float)x, (float)y, (float)z, 1.0F);
         pos.mul(this.projectionViewMatrix);
         if (pos.w == 0.0F) {
            return null;
         } else {
            pos.div(pos.w);
            return new Vec3(pos.x, pos.y, pos.z);
         }
      }
   }
}
