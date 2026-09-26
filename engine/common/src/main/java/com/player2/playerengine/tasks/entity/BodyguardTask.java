package com.player2.playerengine.tasks.entity;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.tasks.movement.GetToEntityTask;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Stay by the owner and attack whatever threatens them, continuously and without asking the model.
 *
 * <h2>Why this is code and not a skill</h2>
 *
 * The Home Guard skill asked the model to "engage it if it gets close enough to threaten your owner".
 * The model only gets a turn when something happens (a chat line, a command finishing), so while the
 * companion quietly followed, nothing was deciding anything. On 2026-09-26 on holly a creeper walked
 * up and hurt both players. Worse, the companion's own combat reflex ({@code MobDefenseChain}) looks
 * only at hostiles near HER, and runs from a fusing creeper within 16 blocks unless she has a shield,
 * so she backed away from the one thing she was there to stop.
 *
 * <p>This task decides every tick: threats are judged by their relation to the OWNER, and while it
 * runs the combat chain is told not to retreat ({@link com.player2.playerengine.chains.MobDefenseChain#guardFor}).
 * That permission is re-armed every tick and lapses on its own when the task stops, so it can never
 * outlive the job. She fights the way she always does (hit, step back while the creeper's fuse
 * resets, hit again); only the retreat is removed. Below {@link #MIN_HEALTH} she stops taking fights
 * and normal self-preservation comes back.
 */
public class BodyguardTask extends Task {

   /** How far from the owner a threat is looked for, and the most she will chase from them. */
   static final double GUARD_RADIUS = 12.0;
   /** Anything hostile this close to the owner is a threat, whatever it is looking at. */
   static final double CLOSE_RADIUS = 6.0;
   /** Where she stands when there is nothing to fight. */
   static final double FOLLOW_DISTANCE = 3.0;
   /**
    * Two hearts. A bodyguard takes the hit rather than run (the user's words: "they shouldn't straight
    * up flee from danger"), so she fights on well past where her own survival logic would leave. Only
    * one hit from death, where she is no use to anyone, does that logic come back.
    */
   static final float MIN_HEALTH = 4.0F;

   private boolean reportedLowHealth = false;

   @Override
   protected void onStart() {
      this.reportedLowHealth = false;
   }

   @Override
   protected Task onTick() {
      PlayerEngineController mod = this.controller;
      Player owner = mod.getOwner();
      if (owner == null || !owner.isAlive() || owner.level() != mod.getWorld()) {
         this.setDebugState("Waiting for my owner to be here.");
         return null;
      }

      LivingEntity self = mod.getEntity();
      boolean healthy = self != null && self.getHealth() >= MIN_HEALTH;
      if (!healthy) {
         if (!this.reportedLowHealth) {
            this.reportedLowHealth = true;
            mod.logAgentNotice("Too hurt to keep fighting for your owner (under two hearts): staying close but"
                  + " not taking fights until healed. Say so if they are in danger.");
         }
      } else {
         this.reportedLowHealth = false;
         mod.getMobDefenseChain().guardFor(mod, 1.0);
         Optional<LivingEntity> threat = pickThreat(mod, owner, self);
         if (threat.isPresent()) {
            this.setDebugState("Protecting " + owner.getName().getString() + " from "
                  + threat.get().getType().getDescriptionId());
            return new KillEntityTask(threat.get());
         }
      }

      this.setDebugState("Guarding " + owner.getName().getString());
      return new GetToEntityTask(owner, FOLLOW_DISTANCE);
   }

   /**
    * The most urgent threat to the owner: a creeper first (it is the one that cannot be tanked), then
    * whatever is nearest the owner.
    */
   private static Optional<LivingEntity> pickThreat(PlayerEngineController mod, Player owner, LivingEntity self) {
      return mod.getWorld()
            .getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(GUARD_RADIUS),
                  mob -> isThreat(mob, owner, self))
            .stream()
            .map(mob -> (LivingEntity) mob)
            .min(Comparator.<LivingEntity>comparingInt(e -> e instanceof Creeper ? 0 : 1)
                  .thenComparingDouble(e -> e.distanceToSqr(owner)));
   }

   static boolean isThreat(Mob mob, Player owner, LivingEntity self) {
      if (!mob.isAlive() || mob == self || mob instanceof IAutomatone || !(mob instanceof Enemy)) {
         return false;
      }
      if (mob.distanceTo(owner) > GUARD_RADIUS) {
         return false;
      }
      LivingEntity target = mob.getTarget();
      boolean targetsUs = target == owner || target == self;
      // Endermen and zombified piglins leave people alone until provoked; attacking one that is minding
      // its own business starts the very fight this is meant to prevent.
      if (mob instanceof NeutralMob) {
         return targetsUs;
      }
      return targetsUs || mob instanceof Creeper || mob.distanceTo(owner) <= CLOSE_RADIUS;
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getMobDefenseChain().guardFor(this.controller, 0.0);
   }

   @Override
   protected boolean isEqual(Task other) {
      return other instanceof BodyguardTask;
   }

   @Override
   protected String toDebugString() {
      return "Bodyguarding my owner";
   }
}
