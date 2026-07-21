package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.mixins.LivingEntityMixin;
import adris.altoclef.multiversion.blockpos.BlockPosVer;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.entity.AbstractKillEntityTask;
import adris.altoclef.tasks.entity.DoToClosestEntityTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.resources.CollectBlockByOneTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.time.TimerGame;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class KillEnderDragonTask extends Task {
   private static final String[] DIAMOND_ARMORS = new String[]{"diamond_chestplate", "diamond_leggings", "diamond_helmet", "diamond_boots"};
   private final TimerGame lookDownTimer = new TimerGame(0.5);
   private final Task collectBuildMaterialsTask = new CollectBlockByOneTask.CollectEndStoneTask(100);
   private final KillEnderDragonTask.PunkEnderDragonTask punkTask = new KillEnderDragonTask.PunkEnderDragonTask();
   private BlockPos exitPortalTop;

   private static Task getPickupTaskIfAny(AltoClefController mod, Item... itemsToPickup) {
      for (Item check : itemsToPickup) {
         if (mod.getEntityTracker().itemDropped(check)) {
            return new PickupDroppedItemTask(new ItemTarget(check), true);
         }
      }

      return null;
   }

   @Override
   protected void onStart() {
      AltoClefController mod = this.controller;
      mod.getBehaviour().push();
      mod.getBehaviour().addForceFieldExclusion(entity -> entity instanceof EnderMan || entity instanceof EnderDragon || entity instanceof EnderDragonPart);
      mod.getBehaviour().setPreferredStairs(true);
   }

   @Override
   protected Task onTick() {
      AltoClefController mod = this.controller;
      if (this.exitPortalTop == null) {
         this.exitPortalTop = this.locateExitPortalTop(mod);
      }

      List<Item> toPickUp = new ArrayList<>(
         Arrays.asList(Items.DIAMOND_SWORD, Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET)
      );
      if (StorageHelper.calculateInventoryFoodScore(mod) < 10) {
         toPickUp.addAll(Arrays.asList(Items.BREAD, Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_PORKCHOP));
      }

      Task pickupDrops = getPickupTaskIfAny(mod, toPickUp.toArray(Item[]::new));
      if (pickupDrops != null) {
         this.setDebugState("Picking up drops in end.");
         return pickupDrops;
      } else {
         for (Item armor : ItemHelper.DIAMOND_ARMORS) {
            try {
               if (mod.getItemStorage().hasItem(armor) && !StorageHelper.isArmorEquipped(mod, armor)) {
                  this.setDebugState("Equipping " + armor);
                  return new EquipArmorTask(armor);
               }
            } catch (NullPointerException var9) {
               Debug.logError("NullpointerException that Should never happen.");
               var9.printStackTrace();
            }
         }

         if (!this.isRailingOnDragon() && this.lookDownTimer.elapsed() && !mod.getControllerExtras().isBreakingBlock() && mod.getPlayer().onGround()) {
            this.lookDownTimer.reset();
            mod.getBaritone().getLookBehavior().updateTarget(new Rotation(0.0F, 90.0F), true);
         }

         if (mod.getBlockScanner().anyFound(Blocks.END_PORTAL)) {
            this.setDebugState("Entering portal to beat the game.");
            return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos.above(), false), Blocks.END_PORTAL);
         } else {
            int MINIMUM_BUILDING_BLOCKS = 1;
            if (mod.getEntityTracker().entityFound(EndCrystal.class)
                  && mod.getItemStorage().getItemCount(Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK, Items.END_STONE) < MINIMUM_BUILDING_BLOCKS
               || this.collectBuildMaterialsTask.isActive() && !this.collectBuildMaterialsTask.isFinished()) {
               if (StorageHelper.miningRequirementMetInventory(this.controller, MiningRequirement.WOOD)) {
                  mod.getBehaviour().addProtectedItems(Items.END_STONE);
                  this.setDebugState("Collecting building blocks to pillar to crystals");
                  return this.collectBuildMaterialsTask;
               }
            } else {
               mod.getBehaviour().removeProtectedItems(Items.END_STONE);
            }

            if (mod.getEntityTracker().entityFound(EndCrystal.class)) {
               this.setDebugState("Kamakazeeing crystals");
               return new DoToClosestEntityTask(toDestroy -> {
                  if (toDestroy.closerThan(mod.getPlayer(), 7.0)) {
                     mod.getControllerExtras().attack(toDestroy);
                  }

                  return new GetToBlockTask(toDestroy.blockPosition().offset(1, 0, 0), false);
               }, EndCrystal.class);
            } else if (mod.getEntityTracker().entityFound(EnderDragon.class)) {
               this.setDebugState("Punking dragon");
               return this.punkTask;
            } else {
               this.setDebugState("Couldn't find ender dragon... This can be very good or bad news.");
               return null;
            }
         }
      }
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof KillEnderDragonTask;
   }

   @Override
   protected String toDebugString() {
      return "Killing Ender Dragon";
   }

   private boolean isRailingOnDragon() {
      return this.punkTask.getMode() == KillEnderDragonTask.Mode.RAILING;
   }

   private BlockPos locateExitPortalTop(AltoClefController mod) {
      if (!mod.getChunkTracker().isChunkLoaded(new BlockPos(0, 64, 0))) {
         return null;
      } else {
         int height = WorldHelper.getGroundHeight(mod, 0, 0, Blocks.BEDROCK);
         return height != -1 ? new BlockPos(0, height, 0) : null;
      }
   }

   private static enum Mode {
      WAITING_FOR_PERCH,
      RAILING;
   }

   private class PunkEnderDragonTask extends Task {
      private final HashMap<BlockPos, Double> breathCostMap = new HashMap<>();
      private final TimerGame hitHoldTimer = new TimerGame(0.1);
      private final TimerGame hitResetTimer = new TimerGame(0.4);
      private final TimerGame randomWanderChangeTimeout = new TimerGame(20.0);
      private KillEnderDragonTask.Mode mode = KillEnderDragonTask.Mode.WAITING_FOR_PERCH;
      private BlockPos randomWanderPos;
      private boolean wasHitting;
      private boolean wasReleased;

      public KillEnderDragonTask.Mode getMode() {
         return this.mode;
      }

      private void hit(AltoClefController mod) {
         mod.getExtraBaritoneSettings().setInteractionPaused(true);
         if (!this.wasHitting) {
            this.wasHitting = true;
            this.wasReleased = false;
            this.hitHoldTimer.reset();
            this.hitResetTimer.reset();
            Debug.logInternal("HIT");
            mod.getInputControls().tryPress(Input.CLICK_LEFT);
         }

         if (this.hitHoldTimer.elapsed() && !this.wasReleased) {
            Debug.logInternal("    up");
            this.wasReleased = true;
         }

         if (this.wasHitting && this.hitResetTimer.elapsed() && this.getAttackCooldownProgress(mod.getPlayer(), 0.0F) > 0.99) {
            this.wasHitting = false;
            mod.getExtraBaritoneSettings().setInteractionPaused(false);
            this.hitResetTimer.reset();
         }
      }

      public float getAttackCooldownProgressPerTick(LivingEntity entity) {
         return (float)(1.0 / entity.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
      }

      public float getAttackCooldownProgress(LivingEntity entity, float baseTime) {
         return Mth.clamp((((LivingEntityMixin)entity).getLastAttackedTicks() + baseTime) / this.getAttackCooldownProgressPerTick(entity), 0.0F, 1.0F);
      }

      private void stopHitting(AltoClefController mod) {
         if (this.wasHitting) {
            if (!this.wasReleased) {
               mod.getExtraBaritoneSettings().setInteractionPaused(false);
               this.wasReleased = true;
            }

            this.wasHitting = false;
         }
      }

      @Override
      protected void onStart() {
         this.controller.getBaritone().getCustomGoalProcess().onLostControl();
      }

      @Override
      protected Task onTick() {
         AltoClefController mod = this.controller;
         if (!mod.getEntityTracker().entityFound(EnderDragon.class)) {
            this.setDebugState("No dragon found.");
            return null;
         } else {
            List<EnderDragon> dragons = mod.getEntityTracker().getTrackedEntities(EnderDragon.class);
            if (!dragons.isEmpty()) {
               for (EnderDragon dragon : dragons) {
                  DragonPhaseInstance dragonPhase = dragon.getPhaseManager().getCurrentPhase();
                  boolean perchingOrGettingReady = dragonPhase.getPhase() == EnderDragonPhase.LANDING || dragonPhase.isSitting();
                  switch (this.mode) {
                     case RAILING:
                        if (!perchingOrGettingReady) {
                           Debug.logMessage("Dragon no longer perching.");
                           mod.getBaritone().getCustomGoalProcess().onLostControl();
                           this.mode = KillEnderDragonTask.Mode.WAITING_FOR_PERCH;
                           break;
                        }

                        Entity head = dragon.head;
                        if (head.closerThan(mod.getPlayer(), 7.5) && dragon.dragonDeathTime <= 1) {
                           AbstractKillEntityTask.equipWeapon(mod);
                           Vec3 targetLookPos = head.position().add(0.0, 3.0, 0.0);
                           Rotation targetRotation = RotationUtils.calcRotationFromVec3d(
                              mod.getBaritone().getEntityContext().headPos(), targetLookPos, mod.getBaritone().getEntityContext().entityRotations()
                           );
                           mod.getBaritone().getLookBehavior().updateTarget(targetRotation, true);
                           mod.getBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                           this.hit(mod);
                        } else {
                           this.stopHitting(mod);
                        }

                        if (!mod.getBaritone().getCustomGoalProcess().isActive() && KillEnderDragonTask.this.exitPortalTop != null) {
                           int bottomYDelta = -3;
                           BlockPos closest = null;
                           double closestDist = Double.POSITIVE_INFINITY;

                           for (int dx = -2; dx <= 2; dx++) {
                              for (int dz = -2; dz <= 2; dz++) {
                                 if (Math.abs(dx) != 2 || Math.abs(dz) != 2) {
                                    BlockPos toCheck = KillEnderDragonTask.this.exitPortalTop.offset(dx, bottomYDelta, dz);
                                    double distSq = BlockPosVer.getSquaredDistance(toCheck, head.position());
                                    if (distSq < closestDist) {
                                       closest = toCheck;
                                       closestDist = distSq;
                                    }
                                 }
                              }
                           }

                           if (closest != null) {
                              mod.getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(closest));
                           }
                        }

                        this.setDebugState("Railing on dragon");
                        break;
                     case WAITING_FOR_PERCH:
                        this.stopHitting(mod);
                        if (perchingOrGettingReady) {
                           mod.getBaritone().getCustomGoalProcess().onLostControl();
                           Debug.logMessage("Dragon perching detected. Dabar duosiu į snuki.");
                           this.mode = KillEnderDragonTask.Mode.RAILING;
                        } else {
                           if (this.randomWanderPos != null && WorldHelper.inRangeXZ(mod.getPlayer(), this.randomWanderPos, 2.0)) {
                              this.randomWanderPos = null;
                           }

                           if (this.randomWanderPos != null && this.randomWanderChangeTimeout.elapsed()) {
                              this.randomWanderPos = null;
                              Debug.logMessage("Reset wander pos after timeout, oof");
                           }

                           if (this.randomWanderPos == null) {
                              this.randomWanderPos = this.getRandomWanderPos(mod);
                              this.randomWanderChangeTimeout.reset();
                              mod.getBaritone().getCustomGoalProcess().onLostControl();
                           }

                           if (!mod.getBaritone().getCustomGoalProcess().isActive()) {
                              mod.getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(this.randomWanderPos));
                           }

                           this.setDebugState("Waiting for perch");
                        }
                  }
               }
            }

            return null;
         }
      }

      @Override
      protected void onStop(Task interruptTask) {
         AltoClefController mod = this.controller;
         mod.getBaritone().getCustomGoalProcess().onLostControl();
         mod.getBaritone().getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, false);
         mod.getExtraBaritoneSettings().setInteractionPaused(false);
      }

      @Override
      protected boolean isEqual(Task other) {
         return other instanceof KillEnderDragonTask.PunkEnderDragonTask;
      }

      @Override
      protected String toDebugString() {
         return "Punking the dragon";
      }

      private BlockPos getRandomWanderPos(AltoClefController mod) {
         double RADIUS_RANGE = 45.0;
         double MIN_RADIUS = 7.0;
         BlockPos pos = null;
         int allowed = 5000;

         while (pos == null) {
            if (allowed-- < 0) {
               Debug.logWarning("Failed to find random solid ground in end, this may lead to problems.");
               return null;
            }

            double radius = MIN_RADIUS + (RADIUS_RANGE - MIN_RADIUS) * Math.random();
            double angle = (Math.PI * 2) * Math.random();
            int x = (int)(radius * Math.cos(angle));
            int z = (int)(radius * Math.sin(angle));
            int y = WorldHelper.getGroundHeight(mod, x, z);
            if (y != -1) {
               BlockPos check = new BlockPos(x, y, z);
               if (mod.getWorld().getBlockState(check).getBlock() == Blocks.END_STONE) {
                  pos = check.above();
               }
            }
         }

         return pos;
      }
   }
}
