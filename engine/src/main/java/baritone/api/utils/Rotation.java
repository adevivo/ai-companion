package baritone.api.utils;

public class Rotation {
   private float yaw;
   private float pitch;

   public Rotation(float yaw, float pitch) {
      this.yaw = yaw;
      this.pitch = pitch;
      if (Float.isInfinite(yaw) || Float.isNaN(yaw) || Float.isInfinite(pitch) || Float.isNaN(pitch)) {
         throw new IllegalStateException(yaw + " " + pitch);
      }
   }

   public float getYaw() {
      return this.yaw;
   }

   public float getPitch() {
      return this.pitch;
   }

   public Rotation add(Rotation other) {
      return new Rotation(this.yaw + other.yaw, this.pitch + other.pitch);
   }

   public Rotation subtract(Rotation other) {
      return new Rotation(this.yaw - other.yaw, this.pitch - other.pitch);
   }

   public Rotation clamp() {
      return new Rotation(this.yaw, clampPitch(this.pitch));
   }

   public Rotation normalize() {
      return new Rotation(normalizeYaw(this.yaw), this.pitch);
   }

   public Rotation normalizeAndClamp() {
      return new Rotation(normalizeYaw(this.yaw), clampPitch(this.pitch));
   }

   public boolean isReallyCloseTo(Rotation other) {
      return this.yawIsReallyClose(other) && Math.abs(this.pitch - other.pitch) < 0.01;
   }

   public boolean yawIsReallyClose(Rotation other) {
      float yawDiff = Math.abs(normalizeYaw(this.yaw) - normalizeYaw(other.yaw));
      return yawDiff < 0.01 || yawDiff > 359.99;
   }

   public static float clampPitch(float pitch) {
      return Math.max(-90.0F, Math.min(90.0F, pitch));
   }

   public static float normalizeYaw(float yaw) {
      float newYaw = yaw % 360.0F;
      if (newYaw < -180.0F) {
         newYaw += 360.0F;
      }

      if (newYaw > 180.0F) {
         newYaw -= 360.0F;
      }

      return newYaw;
   }

   @Override
   public String toString() {
      return "Yaw: " + this.yaw + ", Pitch: " + this.pitch;
   }
}
