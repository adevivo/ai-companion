package adris.altoclef.chains;

import adris.altoclef.AltoClefController;
import adris.altoclef.Debug;
import adris.altoclef.control.KillAura;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.player2api.BehaviorConfig;
import adris.altoclef.tasks.construction.ProjectileProtectionWallTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.movement.CustomBaritoneGoalTask;
import adris.altoclef.tasks.movement.DodgeProjectilesTask;
import adris.altoclef.tasks.movement.RunAwayFromCreepersTask;
import adris.altoclef.tasks.movement.RunAwayFromHostilesTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.baritone.CachedProjectile;
import adris.altoclef.util.helpers.BaritoneHelper;
import adris.altoclef.util.helpers.EntityHelper;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.helpers.ProjectileHelper;
import adris.altoclef.util.helpers.StorageHelper;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.slots.PlayerSlot;
import adris.altoclef.util.slots.Slot;
import baritone.api.IBaritone;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class MobDefenseChain extends SingleTaskChain {
   private static final double DANGER_KEEP_DISTANCE = 30.0;
   private static final double CREEPER_KEEP_DISTANCE = 10.0;
   private static final double ARROW_KEEP_DISTANCE_HORIZONTAL = 2.0;
   private static final double ARROW_KEEP_DISTANCE_VERTICAL = 10.0;
   private static final double SAFE_KEEP_DISTANCE = 8.0;
   private static final List<Class<? extends Entity>> ignoredMobs = List.of(
      Warden.class,
      WitherBoss.class,
      EnderMan.class,
      Blaze.class,
      WitherSkeleton.class,
      Hoglin.class,
      Zoglin.class,
      PiglinBrute.class,
      Vindicator.class,
      MagmaCube.class
   );
   private static boolean shielding = false;
   private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
   private final KillAura killAura = new KillAura();
   private Entity targetEntity;
   private boolean doingFunkyStuff = false;
   private boolean wasPuttingOutFire = false;
   private CustomBaritoneGoalTask runAwayTask;
   private float prevHealth = 20.0F;
   private boolean needsChangeOnAttack = false;
   private Entity lockedOnEntity = null;
   private float cachedLastPriority;

   public MobDefenseChain(TaskRunner runner) {
      super(runner);
   }

   public static double getCreeperSafety(Vec3 pos, Creeper creeper) {
      double distance = creeper.distanceToSqr(pos);
      float fuse = creeper.getSwelling(1.0F);
      return fuse <= 0.001F ? distance : distance * 0.2;
   }

   private static void startShielding(AltoClefController mod) {
      shielding = true;
      ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
      mod.getExtraBaritoneSettings().setInteractionPaused(true);
      if (!mod.getPlayer().isBlocking()) {
         ItemStack handItem = StorageHelper.getItemStackInSlot(PlayerSlot.getEquipSlot(mod.getInventory()));
         if (ItemVer.isFood(handItem)) {
            for (ItemStack spaceSlot : mod.getItemStorage().getItemStacksPlayerInventory(false)) {
               if (spaceSlot.isEmpty()) {
                  mod.getSlotHandler().clickSlot(PlayerSlot.getEquipSlot(mod.getInventory()), 0, ClickType.QUICK_MOVE);
                  return;
               }
            }

            Optional<Slot> garbage = StorageHelper.getGarbageSlot(mod);
            garbage.ifPresent(slot -> mod.getSlotHandler().forceEquipItem(StorageHelper.getItemStackInSlot(slot).getItem()));
         }
      }

      mod.getInputControls().hold(Input.SNEAK);
      mod.getInputControls().hold(Input.CLICK_RIGHT);
   }

   /** Below this fraction of max health the companion runs regardless of what it is carrying. */
   private static final float FLEE_HEALTH_FLOOR = 0.25F;

   /**
    * Once a flee/fight decision is made it holds for this many ticks.
    *
    * <p>Health is a continuous input, so without this the decision oscillates on the boundary: run,
    * regenerate half a heart, turn and fight, take a hit, run again. One second of commitment is
    * enough to break that up and short enough that a companion still reacts to a fight going bad.
    */
   private static final int DECISION_HOLD_TICKS = 20;

   /**
    * How long a flee can make no progress before the companion gives up and fights.
    *
    * <p>This is the cornered case: backed into a dead end, walled in, or fenced. Deliberately
    * deterministic rather than a judgement call routed through the LLM — a round trip is seconds and a
    * cornered companion is dead well before the reply lands. Two seconds of getting nowhere while
    * something is hunting you is not ambiguous enough to need an opinion.
    */
   private static final int CORNERED_TICKS = 40;

   /** Ticks remaining on the current flee/fight decision. See {@link #DECISION_HOLD_TICKS}. */
   private int decisionHoldTicks;
   /** Whether the held decision was "flee". Only meaningful while {@link #decisionHoldTicks} > 0. */
   private boolean heldDecisionWasFlee;
   /** Consecutive ticks spent fleeing without getting further from the nearest hostile. */
   private int noFleeProgressTicks;
   /** Distance to the nearest hostile last tick, for the cornered check. */
   private double lastFleeDistance = -1.0;
   /** Server tick at which a {@code stand_ground} override expires; 0 when none is active. */
   private long standGroundUntilTick;
   /** True while a flee was suppressed, so the reason can be reported once rather than every tick. */
   private boolean reportedStandReason;
   /** Whether this encounter's cornered fallback has already been reported. */
   private boolean reportedCornered;
   /** Whether this encounter's retreat has already been reported. */
   private boolean reportedFleeing;

   /**
    * Suppress the flee branch for {@code seconds}, then let it resume on its own.
    *
    * <p>The LLM's seam into fight-or-flight. It cannot react inside a fight — a round trip is seconds
    * — but it can decide beforehand that this particular fight is worth not running from: holding a
    * doorway while its owner gets clear, defending something that matters, buying time. That is a
    * decision about intent, which is what the model is actually good for.
    *
    * <p>Expiry is the important half. A permanent override is how a companion ends up dying for a
    * fight nobody remembers starting, so this always runs out and the survival instinct comes back
    * without anyone having to remember to switch it off.
    */
   public void standGroundFor(AltoClefController mod, double seconds) {
      long ticks = (long)Math.max(1.0, Math.min(seconds, 300.0) * 20.0);
      this.standGroundUntilTick = mod.getWorld().getGameTime() + ticks;
      this.reportedStandReason = false;
   }

   /** Whether a {@code stand_ground} override is currently suppressing retreat. */
   public boolean isStandingGround(AltoClefController mod) {
      return this.standGroundUntilTick > 0L && mod.getWorld().getGameTime() < this.standGroundUntilTick;
   }

   /** Seconds left on the current {@code stand_ground} override, or 0 if none is active. */
   public double standGroundSecondsLeft(AltoClefController mod) {
      if (!this.isStandingGround(mod)) {
         return 0.0;
      }
      return (this.standGroundUntilTick - mod.getWorld().getGameTime()) / 20.0;
   }

   /**
    * Whether to run from {@code toDealWithList}, given what the companion is carrying <em>and how hurt
    * it is</em>.
    *
    * <p>The health term is the point. This comparison used to weigh gear against crowd size and never
    * look at health at all, which meant a companion at one heart with a diamond sword stood and fought
    * a zombie while a healthy one with bare hands ran from the same zombie. That was survivable only
    * because combat used to let it stunlock anything it engaged; once that went, nothing was left to
    * make a hurt companion disengage.
    *
    * <p>There is deliberately no re-engagement machinery — no memory of what it fled from, no task to
    * return to the fight. This runs every tick, the companion heals while it retreats, and if a mob
    * follows it and it has recovered enough that its gear says it can win, it turns and fights exactly
    * as it would have on first contact. Still hurt, it keeps running. The behaviour people expect
    * falls out of one health-aware check rather than being built as a second system.
    */
   private boolean shouldFlee(AltoClefController mod, List<LivingEntity> toDealWithList) {
      LivingEntity self = mod.getPlayer();
      float max = self.getMaxHealth();
      float frac = max <= 0.0F ? 1.0F : self.getHealth() / max;

      if (frac < FLEE_HEALTH_FLOOR) {
         return true;
      }

      int armor = self.getArmorValue();
      TieredItem bestWeapon = getBestWeapon(mod);
      float damage = bestWeapon == null ? 0.0F : bestWeapon.getTier().getAttackDamageBonus() + 1.0F;
      int shield = hasShield(mod) && bestWeapon != null ? 3 : 0;
      // Gear score plus what the body itself is worth, the whole thing scaled by how much of that body
      // is left. The bravery term matters: without it the inherited scoring rates an unarmoured
      // companion with a wooden sword at one hostile, and a playtest had one flee at full health from a
      // spider and a zombie that it then killed easily the moment being cornered forced it to try.
      // See BehaviorConfig.defenseBravery.
      int canDealWith = (int)Math.ceil((armor * 3.6 / 20.0 + damage * 0.8 + shield
            + BehaviorConfig.defenseBravery) * frac);
      return canDealWith < getDangerousnessScore(toDealWithList);
   }

   /**
    * {@link #shouldFlee} with the oscillation damper and the cornered escape hatch applied.
    *
    * <p>Returns true only if the companion should be running <em>right now</em>. A flee that has made
    * no headway for {@link #CORNERED_TICKS} converts to a fight, because standing still while
    * something hits you is strictly worse than swinging back.
    */
   private boolean shouldFleeNow(AltoClefController mod, List<LivingEntity> toDealWithList) {
      if (this.isStandingGround(mod)) {
         if (!this.reportedStandReason) {
            this.reportedStandReason = true;
            mod.logAgentNotice("Standing ground instead of retreating ("
                  + String.format("%.0f", this.standGroundSecondsLeft(mod)) + "s left).");
         }
         return false;
      }

      if (this.noFleeProgressTicks >= CORNERED_TICKS) {
         // Once per encounter, not once per re-decision: a companion stuck in a dead end cycles
         // flee -> cornered -> fight -> flee every few seconds, and each pass would report itself.
         // Cleared in tickRetreatState when nothing is hunting it any more.
         if (!this.reportedCornered) {
            this.reportedCornered = true;
            mod.logAgentNotice("Cornered — could not get away, so fighting instead.");
         }
         this.noFleeProgressTicks = 0;
         this.decisionHoldTicks = DECISION_HOLD_TICKS;
         this.heldDecisionWasFlee = false;
         return false;
      }

      if (this.decisionHoldTicks > 0) {
         return this.heldDecisionWasFlee;
      }

      boolean flee = this.shouldFlee(mod, toDealWithList);
      // Report once per encounter, not per decision. Transition-only was not enough on its own: the
      // cornered fallback clears heldDecisionWasFlee, so a companion stuck in a dead end cycles
      // flee -> cornered -> flee and announced the retreat again every few seconds. Observed in a
      // playtest as three notices in as many seconds. Cleared in tickRetreatState once nothing is
      // hunting it any more.
      boolean startedFleeing = flee && !this.heldDecisionWasFlee && !this.reportedFleeing;
      this.decisionHoldTicks = DECISION_HOLD_TICKS;
      this.heldDecisionWasFlee = flee;
      if (startedFleeing) {
         this.reportedFleeing = true;
         float pct = mod.getPlayer().getHealth() / Math.max(1.0F, mod.getPlayer().getMaxHealth()) * 100.0F;
         mod.logAgentNotice("Retreating from " + toDealWithList.size() + " hostile(s) at "
               + String.format("%.0f", pct) + "% health.");
      }
      return flee;
   }

   /**
    * Per-tick bookkeeping for the retreat logic: decision hold, and whether a flee is getting anywhere.
    *
    * <p>"Getting anywhere" is measured against the nearest hostile rather than against a destination,
    * because the destination is a Baritone goal that may legitimately be unreachable. Gaining ground on
    * the thing chasing you is the only progress that matters.
    */
   private void tickRetreatState(AltoClefController mod) {
      if (this.decisionHoldTicks > 0) {
         this.decisionHoldTicks--;
      }

      if (this.runAwayTask == null) {
         this.noFleeProgressTicks = 0;
         this.lastFleeDistance = -1.0;
         return;
      }

      double nearest = Double.MAX_VALUE;
      for (LivingEntity hostile : mod.getEntityTracker().getHostiles()) {
         if (hostile != mod.getEntity()) {
            nearest = Math.min(nearest, hostile.distanceToSqr(mod.getPlayer()));
         }
      }
      if (nearest == Double.MAX_VALUE) {
         // Nothing is hunting it any more: the encounter is over, so the next one may report itself.
         this.noFleeProgressTicks = 0;
         this.lastFleeDistance = -1.0;
         this.reportedCornered = false;
         this.reportedFleeing = false;
         return;
      }

      // A quarter of a block squared of slack, so ordinary pathing jitter does not read as progress.
      if (this.lastFleeDistance >= 0.0 && nearest <= this.lastFleeDistance + 0.25) {
         this.noFleeProgressTicks++;
      } else {
         this.noFleeProgressTicks = 0;
      }
      this.lastFleeDistance = nearest;
   }

   private static int getDangerousnessScore(List<LivingEntity> toDealWithList) {
      int numberOfProblematicEntities = toDealWithList.size();

      for (LivingEntity toDealWith : toDealWithList) {
         if (toDealWith instanceof EnderMan || toDealWith instanceof Slime || toDealWith instanceof Blaze) {
            numberOfProblematicEntities++;
         } else if (toDealWith instanceof Drowned && toDealWith.getAllSlots() == Items.TRIDENT) {
            numberOfProblematicEntities += 5;
         }
      }

      return numberOfProblematicEntities;
   }

   @Override
   public float getPriority() {
      // Before the decision, not after: the cornered check and the decision hold both have to be
      // current when getPriorityInner() asks whether to run.
      if (this.controller != null && this.controller.getPlayer() != null) {
         this.tickRetreatState(this.controller);
      }

      this.cachedLastPriority = this.getPriorityInner();
      if (this.getCurrentTask() == null) {
         this.cachedLastPriority = 0.0F;
      }

      this.prevHealth = this.controller.getPlayer().getHealth();
      return this.cachedLastPriority;
   }

   private void stopShielding(AltoClefController mod) {
      if (shielding) {
         ItemStack cursor = StorageHelper.getItemStackInCursorSlot(this.controller);
         if (ItemVer.isFood(cursor)) {
            Optional<Slot> toMoveTo = mod.getItemStorage().getSlotThatCanFitInPlayerInventory(cursor, false).or(() -> StorageHelper.getGarbageSlot(mod));
            if (toMoveTo.isPresent()) {
               Slot garbageSlot = toMoveTo.get();
               mod.getSlotHandler().clickSlot(garbageSlot, 0, ClickType.PICKUP);
            }
         }

         mod.getInputControls().release(Input.SNEAK);
         mod.getInputControls().release(Input.CLICK_RIGHT);
         mod.getExtraBaritoneSettings().setInteractionPaused(false);
         shielding = false;
      }
   }

   public boolean isShielding() {
      return shielding || this.killAura.isShielding();
   }

   private boolean escapeDragonBreath(AltoClefController mod) {
      this.dragonBreathTracker.updateBreath(mod);

      for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(mod.getPlayer())) {
         if (this.dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
            return true;
         }
      }

      return false;
   }

   private float getPriorityInner() {
      if (!AltoClefController.inGame()) {
         return Float.NEGATIVE_INFINITY;
      } else {
         AltoClefController mod = this.controller;
         if (!mod.getModSettings().isMobDefense()) {
            return Float.NEGATIVE_INFINITY;
         } else if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            return Float.NEGATIVE_INFINITY;
         } else {
            if (this.needsChangeOnAttack && (mod.getPlayer().getHealth() < this.prevHealth || this.killAura.attackedLastTick)) {
               this.needsChangeOnAttack = false;
            }

            BlockPos fireBlock = this.isInsideFireAndOnFire(mod);
            if (fireBlock != null) {
               this.putOutFire(mod, fireBlock);
               this.wasPuttingOutFire = true;
            } else {
               mod.getBaritone().getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
               this.wasPuttingOutFire = false;
            }

            Optional<Entity> universallyDangerous = this.getUniversallyDangerousMob(mod);
            if (universallyDangerous.isPresent() && mod.getPlayer().getHealth() <= 10.0F) {
               this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
               this.runAwayTask.controller = this.controller;
               this.setTask(this.runAwayTask);
               return 70.0F;
            } else {
               this.doingFunkyStuff = false;
               Slot offhandSlot = PlayerSlot.getOffhandSlot(mod.getInventory());
               Item offhandItem = StorageHelper.getItemStackInSlot(offhandSlot).getItem();
               Creeper blowingUp = this.getClosestFusingCreeper(mod);
               if (blowingUp != null && blowingUp.distanceTo(mod.getEntity()) <= 16.0F) {
                  if (mod.getFoodChain().needsToEat() && !(mod.getPlayer().getHealth() < 9.0F)
                     || !hasShield(mod)
                     || mod.getEntityTracker().entityFound(ThrownPotion.class)
                     || !mod.getBaritone().getPathingBehavior().isSafeToCancel()
                     || !(blowingUp.getSwelling(blowingUp.getSwellDir()) > 0.5)) {
                     this.doingFunkyStuff = true;
                     this.runAwayTask = new RunAwayFromCreepersTask(10.0);
                     this.runAwayTask.controller = this.controller;
                     this.setTask(this.runAwayTask);
                     return 50.0F + blowingUp.getSwelling(1.0F) * 50.0F;
                  }

                  LookHelper.lookAt(mod, blowingUp.getEyePosition());
                  ItemStack shieldSlot = StorageHelper.getItemStackInSlot(offhandSlot);
                  if (shieldSlot.getItem() != Items.SHIELD) {
                     mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                  } else {
                     startShielding(mod);
                  }
               }

               synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                  if (mod.getModSettings().isDodgeProjectiles()
                     && hasShield(mod)
                     && mod.getBaritone().getPathingBehavior().isSafeToCancel()
                     && !mod.getEntityTracker().entityFound(ThrownPotion.class)
                     && this.isProjectileClose(mod)) {
                     ItemStack shieldSlot = StorageHelper.getItemStackInSlot(new Slot(mod.getInventory().offHand, 0));
                     if (shieldSlot.getItem() != Items.SHIELD) {
                        mod.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                     } else {
                        startShielding(mod);
                     }

                     return 60.0F;
                  }

                  if (blowingUp == null && !this.isProjectileClose(mod)) {
                     this.stopShielding(mod);
                  }
               }

               if (!mod.getFoodChain().needsToEat()
                  && !mod.getMLGBucketChain().isFalling(mod)
                  && mod.getMLGBucketChain().doneMLG()
                  && !mod.getMLGBucketChain().isChorusFruiting()) {
                  this.doForceField(mod);
                  // Projectile walls and arrow-dodging are flight, not defence — both abandon the
                  // current task to reposition. Gated with the rest of the flee behaviour.
                  if (BehaviorConfig.defenseFleeFromHostiles && mod.getPlayer().getHealth() <= 10.0F && !hasShield(mod)) {
                     if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 0
                        && !mod.getFoodChain().needsToEat()
                        && mod.getModSettings().isDodgeProjectiles()
                        && this.isProjectileClose(mod)) {
                        this.doingFunkyStuff = true;
                        this.setTask(new ProjectileProtectionWallTask(mod));
                        return 65.0F;
                     }

                     if (this.isProjectileClose(mod)) {
                        this.runAwayTask = new DodgeProjectilesTask(2.0, 10.0);
                        this.runAwayTask.controller = this.controller;
                        this.setTask(this.runAwayTask);
                        return 65.0F;
                     }
                  }

                  if (!this.isInDanger(mod)
                     || this.escapeDragonBreath(mod)
                     || mod.getFoodChain().isShouldStop()
                     || this.targetEntity != null && !WorldHelper.isSurroundedByHostiles(mod)) {
                     if (mod.getModSettings().shouldDealWithAnnoyingHostiles()) {
                        List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();
                        List<LivingEntity> toDealWithList = new ArrayList<>();
                        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
                           for (LivingEntity hostile : hostiles) {
                              if (hostile != mod.getEntity()) {
                                 boolean isRangedOrPoisonous = hostile instanceof Skeleton
                                    || hostile instanceof Witch
                                    || hostile instanceof Pillager
                                    || hostile instanceof Piglin
                                    || hostile instanceof Stray
                                    || hostile instanceof CaveSpider;
                                 int annoyingRange = 10;
                                 if (isRangedOrPoisonous) {
                                    annoyingRange = 20;
                                    if (!hasShield(mod)) {
                                       annoyingRange = 35;
                                    }
                                 }

                                 if (hostile.closerThan(mod.getPlayer(), annoyingRange) && LookHelper.seesPlayer(hostile, mod.getPlayer(), annoyingRange)) {
                                    boolean isIgnored = false;

                                    for (Class<? extends Entity> ignored : ignoredMobs) {
                                       if (ignored.isInstance(hostile)) {
                                          isIgnored = true;
                                          break;
                                       }
                                    }

                                    if (isIgnored) {
                                       if (mod.getPlayer().getHealth() <= 10.0F) {
                                          toDealWithList.add(hostile);
                                       }
                                    } else {
                                       toDealWithList.add(hostile);
                                    }
                                 }
                              }
                           }
                        }

                        toDealWithList.sort(Comparator.comparingDouble(entity -> mod.getPlayer().distanceTo(entity)));
                        if (!toDealWithList.isEmpty()) {
                           if (BehaviorConfig.defenseFleeFromHostiles
                              && this.shouldFleeNow(mod, toDealWithList)
                              && !this.needsChangeOnAttack) {
                              this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
                              this.runAwayTask.controller = this.controller;
                              this.setTask(this.runAwayTask);
                              return 80.0F;
                           }

                           // Outmatched but not allowed to flee: fall through and fight. If fighting back
                           // is off too, yield the chain entirely so whatever the owner asked for keeps
                           // running — the kill aura still swings at anything already in arm's reach.
                           if (!BehaviorConfig.defenseFightBack) {
                              this.runAwayTask = null;
                              this.needsChangeOnAttack = false;
                              this.lockedOnEntity = null;
                              return 0.0F;
                           }

                           if (!(this.mainTask instanceof KillEntitiesTask)) {
                              this.needsChangeOnAttack = true;
                           }

                           this.runAwayTask = null;
                           Entity toKill = (Entity)toDealWithList.get(0);
                           this.lockedOnEntity = toKill;
                           this.setTask(new KillEntitiesTask(toKill.getClass()));
                           return 65.0F;
                        }
                     }

                     if (this.runAwayTask != null && !this.runAwayTask.isFinished()) {
                        this.setTask(this.runAwayTask);
                        return this.cachedLastPriority;
                     } else {
                        this.runAwayTask = null;
                        if (BehaviorConfig.defenseFightBack
                           && this.needsChangeOnAttack
                           && this.lockedOnEntity != null
                           && this.lockedOnEntity.isAlive()) {
                           this.setTask(new KillEntitiesTask(this.lockedOnEntity.getClass()));
                           return 65.0F;
                        } else {
                           this.needsChangeOnAttack = false;
                           this.lockedOnEntity = null;
                           return 0.0F;
                        }
                     }
                  } else if (BehaviorConfig.defenseFleeFromHostiles) {
                     this.runAwayTask = new RunAwayFromHostilesTask(30.0, true);
                     this.runAwayTask.controller = this.controller;
                     this.setTask(this.runAwayTask);
                     return 70.0F;
                  } else {
                     // Surrounded, and not allowed to run. Stand and keep working; the kill aura is
                     // already swinging at whatever is in reach.
                     this.runAwayTask = null;
                     return 0.0F;
                  }
               } else {
                  this.killAura.stopShielding(mod);
                  this.stopShielding(mod);
                  return Float.NEGATIVE_INFINITY;
               }
            }
         }
      }
   }

   /**
    * Whether shield tactics are available. Gated on {@code behavior.defenseUseShield}: with it off,
    * every shield-aware decision below behaves as though the companion carries no shield, including its
    * estimate of what it can take on — which is the honest consequence of not being allowed to block.
    */
   private static boolean hasShield(AltoClefController mod) {
      if (!BehaviorConfig.defenseUseShield) {
         return false;
      }

      return mod.getItemStorage().hasItem(Items.SHIELD) || mod.getItemStorage().hasItemInOffhand(mod, Items.SHIELD);
   }

   public static TieredItem getBestWeapon(AltoClefController mod) {
      Item[] WEAPONS = new Item[]{
         Items.NETHERITE_SWORD,
         Items.NETHERITE_AXE,
         Items.DIAMOND_SWORD,
         Items.DIAMOND_AXE,
         Items.IRON_SWORD,
         Items.IRON_AXE,
         Items.GOLDEN_SWORD,
         Items.GOLDEN_AXE,
         Items.STONE_SWORD,
         Items.STONE_AXE,
         Items.WOODEN_SWORD,
         Items.WOODEN_AXE
      };
      TieredItem bestSword = null;

      for (Item item : WEAPONS) {
         if (mod.getItemStorage().hasItem(item)) {
            bestSword = (TieredItem)item;
            break;
         }
      }

      return bestSword;
   }

   private BlockPos isInsideFireAndOnFire(AltoClefController mod) {
      boolean onFire = mod.getPlayer().isOnFire();
      if (!onFire) {
         return null;
      } else {
         BlockPos p = mod.getPlayer().blockPosition();
         BlockPos[] toCheck = new BlockPos[]{
            p,
            p.offset(1, 0, 0),
            p.offset(1, 0, -1),
            p.offset(0, 0, -1),
            p.offset(-1, 0, -1),
            p.offset(-1, 0, 0),
            p.offset(-1, 0, 1),
            p.offset(0, 0, 1),
            p.offset(1, 0, 1)
         };

         for (BlockPos check : toCheck) {
            Block b = mod.getWorld().getBlockState(check).getBlock();
            if (b instanceof BaseFireBlock) {
               return check;
            }
         }

         return null;
      }
   }

   private void putOutFire(AltoClefController mod, BlockPos pos) {
      Optional<Rotation> reach = LookHelper.getReach(mod, pos);
      if (reach.isPresent()) {
         IBaritone b = mod.getBaritone();
         if (LookHelper.isLookingAt(mod, pos)) {
            ((PathingBehavior)b.getPathingBehavior()).requestPause();
            b.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            return;
         }

         LookHelper.lookAt(this.controller, reach.get());
      }
   }

   private void doForceField(AltoClefController mod) {
      this.killAura.tickStart();
      List<Entity> entities = mod.getEntityTracker().getCloseEntities();

      try {
         for (Entity entity : entities) {
            if (entity != mod.getPlayer()) {
               boolean shouldForce = false;
               if (!mod.getBehaviour().shouldExcludeFromForcefield(entity)) {
                  if (entity instanceof Mob) {
                     if (EntityHelper.isProbablyHostileToPlayer(mod, entity) && LookHelper.seesPlayer(entity, mod.getPlayer(), 10.0)) {
                        shouldForce = true;
                     }
                  } else if (entity instanceof LargeFireball) {
                     shouldForce = true;
                  }

                  if (shouldForce) {
                     this.killAura.applyAura(entity);
                  }
               }
            }
         }
      } catch (Exception var6) {
         var6.printStackTrace();
      }

      this.killAura.tickEnd(mod);
   }

   private Creeper getClosestFusingCreeper(AltoClefController mod) {
      double worstSafety = Double.POSITIVE_INFINITY;
      Creeper target = null;

      try {
         for (Creeper creeper : mod.getEntityTracker().getTrackedEntities(Creeper.class)) {
            if (creeper != null && !(creeper.getSwelling(1.0F) < 0.04)) {
               double safety = getCreeperSafety(mod.getPlayer().position(), creeper);
               if (safety < worstSafety) {
                  target = creeper;
               }
            }
         }

         return target;
      } catch (ArrayIndexOutOfBoundsException | NullPointerException | ConcurrentModificationException var10) {
         Debug.logWarning("Weird Exception caught and ignored while scanning for creepers: " + var10.getMessage());
         return target;
      }
   }

   private boolean isProjectileClose(AltoClefController mod) {
      List<CachedProjectile> projectiles = mod.getEntityTracker().getProjectiles();

      try {
         for (CachedProjectile projectile : projectiles) {
            if (projectile.position.distanceToSqr(mod.getPlayer().position()) < 150.0) {
               boolean isGhastBall = projectile.projectileType == LargeFireball.class;
               if (isGhastBall) {
                  Optional<Entity> ghastBall = mod.getEntityTracker().getClosestEntity(LargeFireball.class);
                  Optional<Entity> ghast = mod.getEntityTracker().getClosestEntity(Ghast.class);
                  if (ghastBall.isPresent() && ghast.isPresent() && this.runAwayTask == null && mod.getBaritone().getPathingBehavior().isSafeToCancel()) {
                     ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
                     LookHelper.lookAt(mod, ghast.get().getEyePosition());
                  }

                  return false;
               }

               if (projectile.projectileType != DragonFireball.class) {
                  if (projectile.projectileType == Arrow.class
                     || projectile.projectileType == SpectralArrow.class
                     || projectile.projectileType == SmallFireball.class) {
                     LivingEntity clientPlayerEntity = mod.getPlayer();
                     if (clientPlayerEntity.distanceToSqr(projectile.position) < clientPlayerEntity.distanceToSqr(projectile.position.add(projectile.velocity))
                        )
                      {
                        continue;
                     }
                  }

                  Vec3 expectedHit = ProjectileHelper.calculateArrowClosestApproach(projectile, mod.getPlayer().position());
                  Vec3 delta = mod.getPlayer().position().subtract(expectedHit);
                  double horizontalDistanceSq = delta.x * delta.x + delta.z * delta.z;
                  double verticalDistance = Math.abs(delta.y);
                  if (horizontalDistanceSq < 4.0 && verticalDistance < 10.0) {
                     if (mod.getBaritone().getPathingBehavior().isSafeToCancel() && hasShield(mod)) {
                        ((PathingBehavior)mod.getBaritone().getPathingBehavior()).requestPause();
                        LookHelper.lookAt(mod, projectile.position.add(0.0, 0.3, 0.0));
                     }

                     return true;
                  }
               }
            }
         }
      } catch (ConcurrentModificationException var12) {
         Debug.logWarning(var12.getMessage());
      }

      for (Skeleton skeleton : mod.getEntityTracker().getTrackedEntities(Skeleton.class)) {
         if (!(skeleton.distanceTo(mod.getPlayer()) > 10.0F) && skeleton.hasLineOfSight(mod.getPlayer()) && skeleton.getTicksUsingItem() > 15) {
            return true;
         }
      }

      return false;
   }

   private Optional<Entity> getUniversallyDangerousMob(AltoClefController mod) {
      Class<?>[] dangerousMobs = new Class[]{
         Warden.class, WitherBoss.class, WitherSkeleton.class, Hoglin.class, Zoglin.class, PiglinBrute.class, Vindicator.class
      };
      double range = 6.0;

      for (Class<?> dangerous : dangerousMobs) {
         Optional<Entity> entity = mod.getEntityTracker().getClosestEntity(dangerous);
         if (entity.isPresent() && entity.get().distanceToSqr(mod.getPlayer()) < range * range && EntityHelper.isAngryAtPlayer(mod, entity.get())) {
            return entity;
         }
      }

      return Optional.empty();
   }

   private boolean isInDanger(AltoClefController mod) {
      boolean witchNearby = mod.getEntityTracker().entityFound(Witch.class);
      double safeKeepDistance = 8.0;
      float health = mod.getPlayer().getHealth();
      if (health <= 10.0F && witchNearby) {
         safeKeepDistance = 30.0;
      }

      if (mod.getPlayer().hasEffect(MobEffects.WITHER) || mod.getPlayer().hasEffect(MobEffects.POISON) && witchNearby) {
         safeKeepDistance = 30.0;
      }

      if (WorldHelper.isVulnerable(mod.getPlayer())) {
         try {
            LivingEntity player = mod.getPlayer();
            List<LivingEntity> hostiles = mod.getEntityTracker().getHostiles();
            synchronized (BaritoneHelper.MINECRAFT_LOCK) {
               for (Entity entity : hostiles) {
                  if (entity.closerThan(player, safeKeepDistance)
                     && !mod.getBehaviour().shouldExcludeFromForcefield(entity)
                     && EntityHelper.isAngryAtPlayer(mod, entity)
                     && entity != mod.getPlayer()) {
                     return true;
                  }
               }
            }
         } catch (Exception var13) {
            Debug.logWarning("Weird multithread exception. Will fix later. " + var13.getMessage());
         }
      }

      return false;
   }

   public void setTargetEntity(Entity entity) {
      this.targetEntity = entity;
   }

   public void resetTargetEntity() {
      this.targetEntity = null;
   }

   public void setForceFieldRange(double range) {
      this.killAura.setRange(range);
   }

   public void resetForceField() {
      this.killAura.setRange(Double.POSITIVE_INFINITY);
   }

   public boolean isDoingAcrobatics() {
      return this.doingFunkyStuff;
   }

   public boolean isPuttingOutFire() {
      return this.wasPuttingOutFire;
   }

   @Override
   public boolean isActive() {
      return true;
   }

   @Override
   protected void onTaskFinish(AltoClefController mod) {
   }

   @Override
   public String getName() {
      return "Mob Defense";
   }
}
