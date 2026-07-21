package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.control.InputControls;
import adris.altoclef.multiversion.DamageSourceVer;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.helpers.ConfigHelper;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.MathsHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.serialization.ItemDeserializer;
import adris.altoclef.util.serialization.ItemSerializer;
import baritone.api.IBaritone;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class MLGBucketTask extends Task {
   private static MLGBucketTask.MLGClutchConfig config;
   private BlockPos placedPos;
   private BlockPos movingTorwards;

   private static boolean isLava(AltoClefController controller, BlockPos pos) {
      assert controller.getWorld() != null;

      return controller.getWorld().getBlockState(pos).getBlock() == Blocks.LAVA;
   }

   private static boolean lavaWillProtect(AltoClefController controller, BlockPos pos) {
      assert controller.getWorld() != null;

      BlockState state = controller.getWorld().getBlockState(pos);
      if (state.getBlock() != Blocks.LAVA) {
         return false;
      } else {
         int level = state.getFluidState().getAmount();
         return level == 0 || level >= config.lavaLevelOrGreaterWillCancelFallDamage;
      }
   }

   private static boolean isWater(AltoClefController controller, BlockPos pos) {
      assert controller.getWorld() != null;

      return controller.getWorld().getBlockState(pos).getBlock() == Blocks.WATER;
   }

   private static boolean canTravelToInAir(AltoClefController controller, BlockPos pos) {
      LivingEntity clientPlayerEntity = controller.getPlayer();

      assert clientPlayerEntity != null;

      double verticalDist = clientPlayerEntity.position().y() - pos.getY() - 1.0;
      double verticalVelocity = -1.0 * clientPlayerEntity.getDeltaMovement().y;
      double grav = 0.08;
      double movementSpeedPerTick = config.averageHorizontalMovementSpeedPerTick;
      double ticksToTravelSq = (-verticalVelocity + Math.sqrt(verticalVelocity * verticalVelocity + 2.0 * grav * verticalDist)) / grav;
      double maxMoveDistanceSq = movementSpeedPerTick * movementSpeedPerTick * ticksToTravelSq * ticksToTravelSq;
      double horizontalDistance = WorldHelper.distanceXZ(clientPlayerEntity.position(), WorldHelper.toVec3d(pos)) - 0.8;
      if (horizontalDistance < 0.0) {
         horizontalDistance = 0.0;
      }

      return maxMoveDistanceSq > horizontalDistance * horizontalDistance;
   }

   private static boolean isFallDeadly(AltoClefController controller, BlockPos pos) {
      LivingEntity clientPlayerEntity = controller.getPlayer();
      double damage = calculateFallDamageToLandOn(controller, pos);

      assert controller.getWorld() != null;

      Block b = controller.getWorld().getBlockState(pos).getBlock();
      if (b == Blocks.HAY_BLOCK) {
         damage *= 0.2F;
      }

      assert clientPlayerEntity != null;

      double resultingHealth = clientPlayerEntity.getHealth() - (float)damage;
      return resultingHealth < config.preferLavaWhenFallDropsHealthBelowThreshold;
   }

   private static double calculateFallDamageToLandOn(AltoClefController controller, BlockPos pos) {
      Level world = controller.getWorld();
      LivingEntity clientPlayerEntity = controller.getPlayer();

      assert clientPlayerEntity != null;

      double totalFallDistance = clientPlayerEntity.fallDistance + clientPlayerEntity.getY() - pos.getY() - 1.0;
      double baseFallDamage = Mth.ceil(totalFallDistance - 3.0);

      assert world != null;

      return EntityHelper.calculateResultingPlayerDamage(clientPlayerEntity, DamageSourceVer.getFallDamageSource(world), baseFallDamage);
   }

   private static void moveLeftRight(AltoClefController controller, int delta) {
      InputControls controls = controller.getInputControls();
      if (delta == 0) {
         controls.release(Input.MOVE_LEFT);
         controls.release(Input.MOVE_RIGHT);
      } else if (delta > 0) {
         controls.release(Input.MOVE_LEFT);
         controls.hold(Input.MOVE_RIGHT);
      } else {
         controls.hold(Input.MOVE_LEFT);
         controls.release(Input.MOVE_RIGHT);
      }
   }

   private static void moveForwardBack(AltoClefController controller, int delta) {
      InputControls controls = controller.getInputControls();
      if (delta == 0) {
         controls.release(Input.MOVE_FORWARD);
         controls.release(Input.MOVE_BACK);
      } else if (delta > 0) {
         controls.hold(Input.MOVE_FORWARD);
         controls.release(Input.MOVE_BACK);
      } else {
         controls.release(Input.MOVE_FORWARD);
         controls.hold(Input.MOVE_BACK);
      }
   }

   private Task onTickInternal(AltoClefController mod, BlockPos oldMovingTorwards) {
      Optional<BlockPos> willLandOn = this.getBlockWeWillLandOn(mod);
      Optional<BlockPos> bestClutchPos = this.getBestConeClutchBlock(mod, oldMovingTorwards);
      if (bestClutchPos.isPresent()) {
         this.movingTorwards = bestClutchPos.get().mutable();
         if (!this.movingTorwards.equals(oldMovingTorwards)) {
            if (oldMovingTorwards == null) {
               Debug.logMessage("(NEW clutch target: " + this.movingTorwards + ")");
            } else {
               Debug.logMessage("(changed clutch target: " + this.movingTorwards + ")");
            }
         }
      } else if (oldMovingTorwards != null) {
         Debug.logMessage("(LOST clutch position!)");
      }

      if (willLandOn.isPresent()) {
         this.handleJumpForLand(mod, willLandOn.get());
         return this.placeMLGBucketTask(mod, willLandOn.get());
      } else {
         this.setDebugState("Wait for it...");
         mod.getInputControls().release(Input.JUMP);
         return null;
      }
   }

   private Task placeMLGBucketTask(AltoClefController mod, BlockPos toPlaceOn) {
      if (!this.hasClutchItem(mod)) {
         this.setDebugState("No clutch item");
         return null;
      } else {
         if (!WorldHelper.isSolidBlock(this.controller, toPlaceOn)) {
            toPlaceOn = toPlaceOn.below();
         }

         BlockPos willLandIn = toPlaceOn.above();
         BlockState willLandInState = mod.getWorld().getBlockState(willLandIn);
         if (willLandInState.getBlock() == Blocks.WATER) {
            this.setDebugState("Waiting to fall into water");
            mod.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return null;
         } else {
            IEntityContext ctx = mod.getBaritone().getEntityContext();
            Optional<Rotation> reachable = RotationUtils.reachableCenter(ctx.entity(), toPlaceOn, ctx.playerController().getBlockReachDistance(), false);
            if (reachable.isPresent()) {
               this.setDebugState("Performing MLG");
               LookHelper.lookAt(this.controller, reachable.get());
               boolean hasClutch = !mod.getWorld().dimensionType().ultraWarm() && mod.getSlotHandler().forceEquipItem(Items.WATER_BUCKET);
               if (!hasClutch && !config.clutchItems.isEmpty()) {
                  for (Item tryEquip : config.clutchItems) {
                     if (mod.getSlotHandler().forceEquipItem(tryEquip)) {
                        hasClutch = true;
                        break;
                     }
                  }
               }

               BlockPos[] toCheckLook = new BlockPos[]{toPlaceOn, toPlaceOn.above(), toPlaceOn.above(2)};
               if (hasClutch && Arrays.stream(toCheckLook).anyMatch(check -> mod.getBaritone().getEntityContext().isLookingAt(check))) {
                  Debug.logMessage("HIT: " + willLandIn);
                  this.placedPos = willLandIn;
                  mod.getInputControls().tryPress(Input.CLICK_RIGHT);
               } else {
                  this.setDebugState("NOT LOOKING CORRECTLY!");
               }
            } else {
               this.setDebugState("Waiting to reach target block...");
            }

            return null;
         }
      }
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      mod.getInputControls().hold(Input.SPRINT);
      MutableBlockPos mutable = this.movingTorwards != null ? this.movingTorwards.mutable() : null;
      this.movingTorwards = null;
      Task result = this.onTickInternal(mod, mutable);
      this.handleForwardVelocity(mod, !Objects.equals(mutable, this.movingTorwards));
      this.handleCancellingSidewaysVelocity(mod);
      return result;
   }

   private void handleForwardVelocity(AltoClefController mod, boolean newForwardTarget) {
      if (!mod.getPlayer().onGround() && this.movingTorwards != null && !WorldHelper.inRangeXZ(mod.getPlayer(), this.movingTorwards, 0.05F)) {
         Rotation look = LookHelper.getLookRotation(this.controller);
         look = new Rotation(look.getYaw(), 0.0F);
         Vec3 forwardFacing = LookHelper.toVec3d(look).multiply(1.0, 0.0, 1.0).normalize();
         Vec3 delta = WorldHelper.toVec3d(this.movingTorwards).subtract(mod.getPlayer().position()).multiply(1.0, 0.0, 1.0);
         Vec3 velocity = mod.getPlayer().getDeltaMovement().multiply(1.0, 0.0, 1.0);
         Vec3 pd = delta.subtract(velocity.scale(3.0));
         double forwardStrength = pd.dot(forwardFacing);
         if (newForwardTarget) {
            LookHelper.lookAt(mod, this.movingTorwards);
         }

         Debug.logInternal("F:" + forwardStrength);
         moveForwardBack(mod, (int)Math.signum(forwardStrength));
      } else {
         moveForwardBack(mod, 0);
      }
   }

   @Override
   protected void onStart() {
      this.controller.getBaritone().getPathingBehavior().forceCancel();
      this.placedPos = null;
      this.controller.getPlayer().setXRot(90.0F);
   }

   private void handleJumpForLand(AltoClefController mod, BlockPos willLandOn) {
      BlockPos willLandIn = WorldHelper.isSolidBlock(this.controller, willLandOn) ? willLandOn.above() : willLandOn;
      BlockState s = mod.getWorld().getBlockState(willLandIn);
      if (s.getBlock() == Blocks.LAVA) {
         mod.getInputControls().hold(Input.JUMP);
      } else {
         AABB blockBounds;
         try {
            blockBounds = s.getCollisionShape(mod.getWorld(), willLandIn).bounds();
         } catch (UnsupportedOperationException var7) {
            blockBounds = AABB.ofSize(WorldHelper.toVec3d(willLandIn), 1.0, 1.0, 1.0);
         }

         boolean inside = mod.getPlayer().getBoundingBox().intersects(blockBounds);
         if (inside) {
            mod.getInputControls().hold(Input.JUMP);
         } else {
            mod.getInputControls().release(Input.JUMP);
         }
      }
   }

   private Optional<BlockPos> getBlockWeWillLandOn(AltoClefController mod) {
      Vec3 velCheck = mod.getPlayer().getDeltaMovement();
      velCheck.multiply(10.0, 0.0, 10.0);
      AABB b = mod.getPlayer().getBoundingBox().move(velCheck);
      Vec3 c = b.getCenter();
      Vec3[] coords = new Vec3[]{c, new Vec3(b.minX, c.y, b.minZ), new Vec3(b.maxX, c.y, b.minZ), new Vec3(b.minX, c.y, b.maxZ), new Vec3(b.maxX, c.y, b.maxZ)};
      BlockHitResult result = null;
      double bestSqDist = Double.POSITIVE_INFINITY;

      for (Vec3 rayOrigin : coords) {
         ClipContext rctx = this.castDown(rayOrigin);
         BlockHitResult hit = mod.getWorld().clip(rctx);
         if (hit.getType() == Type.BLOCK) {
            double curDis = hit.getLocation().distanceToSqr(rayOrigin);
            if (curDis < bestSqDist) {
               result = hit;
               bestSqDist = curDis;
            }
         }
      }

      return result != null && result.getType() == Type.BLOCK ? Optional.ofNullable(result.getBlockPos()) : Optional.empty();
   }

   private void handleCancellingSidewaysVelocity(AltoClefController mod) {
      if (this.movingTorwards == null) {
         moveLeftRight(mod, 0);
      } else {
         Vec3 velocity = mod.getPlayer().getDeltaMovement();
         Vec3 deltaTarget = WorldHelper.toVec3d(this.movingTorwards).subtract(mod.getPlayer().position());
         Rotation look = LookHelper.getLookRotation(this.controller);
         Vec3 forwardFacing = LookHelper.toVec3d(look).multiply(1.0, 0.0, 1.0).normalize();
         Vec3 rightVelocity = MathsHelper.projectOntoPlane(velocity, forwardFacing).multiply(1.0, 0.0, 1.0);
         Vec3 rightDelta = MathsHelper.projectOntoPlane(deltaTarget, forwardFacing).multiply(1.0, 0.0, 1.0);
         Vec3 pd = rightDelta.subtract(rightVelocity.scale(2.0));
         Vec3 faceRight = forwardFacing.cross(new Vec3(0.0, 1.0, 0.0));
         boolean moveRight = pd.dot(faceRight) > 0.0;
         if (moveRight) {
            moveLeftRight(mod, 1);
         } else {
            moveLeftRight(mod, -1);
         }
      }
   }

   private Optional<BlockPos> getBestConeClutchBlock(AltoClefController mod, BlockPos oldClutchTarget) {
      double pitchHalfWidth = config.epicClutchConePitchAngle;
      double dpitchStart = pitchHalfWidth / config.epicClutchConePitchResolution;
      MLGBucketTask.ConeClutchContext cctx = new MLGBucketTask.ConeClutchContext(mod);
      if (oldClutchTarget != null) {
         cctx.checkBlock(mod, oldClutchTarget);
      }

      for (double pitch = dpitchStart; pitch <= pitchHalfWidth; pitch += pitchHalfWidth / config.epicClutchConePitchResolution) {
         double pitchProgress = (pitch - dpitchStart) / (pitchHalfWidth - dpitchStart);
         double yawResolution = config.epicClutchConeYawDivisionStart
            + pitchProgress * (config.epicClutchConeYawDivisionEnd - config.epicClutchConeYawDivisionStart);

         for (double yaw = 0.0; yaw < 360.0; yaw += 360.0 / yawResolution) {
            ClipContext rctx = this.castCone(yaw, pitch);
            cctx.checkRay(mod, rctx);
         }
      }

      Vec3 center = mod.getPlayer().position();

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            ClipContext ctx = this.castDown(center.add(dx, 0.0, dz));
            cctx.checkRay(mod, ctx);
         }
      }

      return Optional.ofNullable(cctx.bestBlock);
   }

   private ClipContext castDown(Vec3 origin) {
      LivingEntity clientPlayerEntity = this.controller.getPlayer();

      assert clientPlayerEntity != null;

      return new ClipContext(
         origin, origin.add(0.0, -1.0 * config.castDownDistance, 0.0), net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.ANY, clientPlayerEntity
      );
   }

   private ClipContext castCone(double yaw, double pitch) {
      LivingEntity clientPlayerEntity = this.controller.getPlayer();

      assert clientPlayerEntity != null;

      Vec3 origin = clientPlayerEntity.position();
      double dy = config.epicClutchConeCastHeight;
      double dH = dy * Math.sin(Math.toRadians(pitch));
      double yawRad = Math.toRadians(yaw);
      double dx = dH * Math.cos(yawRad);
      double dz = dH * Math.sin(yawRad);
      Vec3 end = origin.add(dx, -1.0 * dy, dz);
      return new ClipContext(origin, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, Fluid.ANY, clientPlayerEntity);
   }

   @Override
   protected void onStop(Task interruptTask) {
      IBaritone baritone = this.controller.getBaritone();
      InputControls controls = this.controller.getInputControls();
      baritone.getPathingBehavior().forceCancel();
      this.movingTorwards = null;
      baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
      moveLeftRight(this.controller, 0);
      moveForwardBack(this.controller, 0);
      controls.release(Input.SPRINT);
      controls.release(Input.JUMP);
   }

   private boolean hasClutchItem(AltoClefController mod) {
      return !mod.getWorld().dimensionType().ultraWarm() && mod.getItemStorage().hasItem(Items.WATER_BUCKET)
         ? true
         : config.clutchItems.stream().anyMatch(item -> mod.getItemStorage().hasItem(item));
   }

   @Override
   public boolean isFinished() {
      LivingEntity player = this.controller.getPlayer();
      return player.isSwimming() || player.isInWater() || player.onGround() || player.onClimbable();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof MLGBucketTask;
   }

   @Override
   protected String toDebugString() {
      String result = "Epic gaemer moment";
      if (this.movingTorwards != null) {
         result = result + " (CLUTCH AT: " + result + ")";
      }

      return result;
   }

   public BlockPos getWaterPlacedPos() {
      return this.placedPos;
   }

   static {
      ConfigHelper.loadConfig(
         "configs/mlg_clutch_settings.json", MLGBucketTask.MLGClutchConfig::new, MLGBucketTask.MLGClutchConfig.class, newConfig -> config = newConfig
      );
   }

   class ConeClutchContext {
      private final boolean hasClutchItem;
      public BlockPos bestBlock = null;
      private double highestY = Double.NEGATIVE_INFINITY;
      private double closestXZ = Double.POSITIVE_INFINITY;
      private boolean bestBlockIsSafe = false;
      private boolean bestBlockIsDeadlyFall = false;
      private boolean bestBlockIsLava = false;

      public ConeClutchContext(AltoClefController mod) {
         this.hasClutchItem = MLGBucketTask.this.hasClutchItem(mod);
      }

      public void checkBlock(AltoClefController mod, BlockPos check) {
         if (!Objects.equals(this.bestBlock, check)) {
            if (WorldHelper.isAir(mod.getWorld().getBlockState(check).getBlock())) {
               Debug.logMessage("(MLG Air block checked for landing, the block broke. We'll try another): " + check);
            } else {
               boolean lava = MLGBucketTask.isLava(MLGBucketTask.this.controller, check);
               boolean lavaWillProtect = lava && MLGBucketTask.lavaWillProtect(MLGBucketTask.this.controller, check);
               boolean water = MLGBucketTask.isWater(MLGBucketTask.this.controller, check);
               boolean isDeadlyFall = !this.hasClutchItem && MLGBucketTask.isFallDeadly(MLGBucketTask.this.controller, check);
               if (!this.bestBlockIsSafe || water) {
                  double height = check.getY();
                  double distSqXZ = WorldHelper.distanceXZSquared(WorldHelper.toVec3d(check), mod.getPlayer().position());
                  boolean highestSoFar = height > this.highestY;
                  boolean closestSoFar = distSqXZ < this.closestXZ;
                  if ((
                        this.bestBlock == null
                           || water && !this.bestBlockIsSafe
                           || lava && lavaWillProtect && this.bestBlockIsDeadlyFall && !this.hasClutchItem
                           || !lava && !isDeadlyFall && (closestSoFar && this.hasClutchItem && highestSoFar || this.bestBlockIsLava)
                     )
                     && MLGBucketTask.canTravelToInAir(MLGBucketTask.this.controller, !lava && !water ? check : check.below())) {
                     if (highestSoFar) {
                        this.highestY = height;
                     }

                     if (closestSoFar) {
                        this.closestXZ = distSqXZ;
                     }

                     this.bestBlockIsSafe = water;
                     this.bestBlockIsDeadlyFall = isDeadlyFall;
                     this.bestBlockIsLava = lava;
                     this.bestBlock = check;
                  }
               }
            }
         }
      }

      public void checkRay(AltoClefController mod, ClipContext rctx) {
         BlockHitResult hit = mod.getWorld().clip(rctx);
         if (hit.getType() == Type.BLOCK) {
            BlockPos check = hit.getBlockPos();
            if (hit.getDirection().getStepY() <= 0) {
               return;
            }

            this.checkBlock(mod, check);
         }
      }
   }

   private static class MLGClutchConfig {
      public double castDownDistance = 40.0;
      public double averageHorizontalMovementSpeedPerTick = 0.25;
      public double epicClutchConeCastHeight = 40.0;
      public double epicClutchConePitchAngle = 25.0;
      public int epicClutchConePitchResolution = 8;
      public int epicClutchConeYawDivisionStart = 6;
      public int epicClutchConeYawDivisionEnd = 20;
      public int preferLavaWhenFallDropsHealthBelowThreshold = 3;
      public int lavaLevelOrGreaterWillCancelFallDamage = 5;
      @JsonSerialize(
         using = ItemSerializer.class
      )
      @JsonDeserialize(
         using = ItemDeserializer.class
      )
      public List<Item> clutchItems = List.of(Items.HAY_BLOCK, Items.TWISTING_VINES);
   }
}
