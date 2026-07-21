package adris.altoclef.chains;

import adris.altoclef.AltoClefController;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.EntitySwungEvent;
import adris.altoclef.eventbus.events.PlayerDamageEvent;
import adris.altoclef.tasks.entity.KillPlayerTask;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.LookHelper;
import adris.altoclef.util.time.TimerGame;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PlayerDefenseChain extends SingleTaskChain {
   private Map<String, PlayerDefenseChain.DamageTarget> damageTargets = new HashMap<>();
   private Map<Integer, TimerGame> recentlySwung = new HashMap<>();
   private TimerGame recentlyDamagedUnknown = new TimerGame(0.3);
   private String currentlyAttackingPlayer = null;
   private static int HITS_BEFORE_RETALIATION = 2;
   private static int HITS_BEFORE_RETALIATION_LOW_HEALTH = 1;
   private static int LOW_HEALTH_THRESHOLD = 14;
   private static double SWING_TIMEOUT = 0.4;
   private AltoClefController mod;

   public PlayerDefenseChain(TaskRunner runner) {
      super(runner);
      this.mod = runner.getMod();
      EventBus.subscribe(PlayerDamageEvent.class, evt -> {
         if (this.controller.getPlayer() == evt.target) {
            this.onPlayerDamage(evt.source.getEntity());
         }
      });
      EventBus.subscribe(EntitySwungEvent.class, evt -> this.onEntitySwung(evt.entity));
   }

   private void processMaybeDamaged() {
      if (this.recentlyDamagedUnknown != null && !this.recentlyDamagedUnknown.elapsed()) {
         this.recentlyDamagedUnknown = null;
         LivingEntity player = this.mod.getPlayer();

         for (Entity entity : this.mod.getWorld().getAllEntities()) {
            if (entity != this.mod.getOwner()) {
               if (entity != null && (!this.recentlySwung.containsKey(entity.getId()) || !this.recentlySwung.get(entity.getId()).elapsed())) {
                  if (!(entity.distanceTo(player) > 5.0F)) {
                     Vec3 playerCenter = player.position().add(new Vec3(0.0, player.getEyeHeight(), 0.0));
                     if (entity.isAlive() && LookHelper.isLookingAt(entity, playerCenter, 60.0)) {
                        this.recentlySwung.remove(entity.getId());
                        this.onPlayerDamage(entity);
                        return;
                     }
                  }
               } else {
                  this.recentlySwung.remove(entity.getId());
               }
            }
         }
      } else {
         this.recentlyDamagedUnknown = null;
      }
   }

   private void onEntitySwung(Entity entity) {
      int id = entity.getId();
      TimerGame timeout = new TimerGame(SWING_TIMEOUT);
      timeout.reset();
      this.recentlySwung.put(id, timeout);
      this.processMaybeDamaged();
   }

   private void onPlayerDamage(Entity damagedBy) {
      if (damagedBy != null) {
         LivingEntity clientPlayer = this.mod.getPlayer();
         this.recentlyDamagedUnknown = null;
         if (damagedBy instanceof Player player) {
            String offendingName = player.getName().getString();
            if (!this.damageTargets.containsKey(offendingName)) {
               this.damageTargets.put(offendingName, new PlayerDefenseChain.DamageTarget());
            }

            PlayerDefenseChain.DamageTarget target = this.damageTargets.get(offendingName);
            if (target.forgetInstigationTimer.elapsed()) {
               target.timesHit = 0;
            }

            if (target.forgetAttackTimer.elapsed()) {
               target.attacking = false;
            }

            target.forgetInstigationTimer.reset();
            if (!target.attacking) {
               target.timesHit++;
               int hitsBeforeRetaliation = clientPlayer.getHealth() < LOW_HEALTH_THRESHOLD ? HITS_BEFORE_RETALIATION_LOW_HEALTH : HITS_BEFORE_RETALIATION;
               System.out
                  .println(
                     "Another player hit us "
                        + target.timesHit
                        + "times: "
                        + offendingName
                        + ", attacking if they hit us "
                        + (hitsBeforeRetaliation - target.timesHit)
                        + " more time(s)."
                  );
               if (target.timesHit >= hitsBeforeRetaliation) {
                  System.out.println("Too many attacks from another player! Retaliating attacks against offending player: " + offendingName);
                  target.attacking = true;
                  target.forgetAttackTimer.reset();
                  target.timesHit = 0;
                  this.currentlyAttackingPlayer = offendingName;
               }
            } else {
               target.forgetAttackTimer.reset();
            }
         }
      } else {
         if (this.recentlyDamagedUnknown == null || this.recentlyDamagedUnknown.elapsed()) {
            this.recentlyDamagedUnknown = new TimerGame(0.3);
            this.recentlyDamagedUnknown.reset();
         }

         this.processMaybeDamaged();
      }
   }

   @Override
   public float getPriority() {
      if (this.currentlyAttackingPlayer != null) {
         Optional<Player> currentPlayerEntity = this.controller.getEntityTracker().getPlayerEntity(this.currentlyAttackingPlayer);
         if (!currentPlayerEntity.isPresent() || !currentPlayerEntity.get().isAlive()) {
            this.currentlyAttackingPlayer = null;
         }
      }

      String[] playerNames = this.damageTargets.keySet().toArray(String[]::new);

      for (String potentialAttacker : playerNames) {
         if (potentialAttacker == null) {
            this.damageTargets.remove(potentialAttacker);
         } else {
            LivingEntity potentialPlayer = (LivingEntity)this.controller.getEntityTracker().getPlayerEntity(potentialAttacker).orElse(null);
            if (potentialPlayer == null || !potentialPlayer.isAlive() || this.damageTargets.get(potentialAttacker).forgetAttackTimer.elapsed()) {
               System.out.println("Either forgot or killed player: " + potentialAttacker + " (no longer attacking)");
               this.damageTargets.remove(potentialAttacker);
               if (potentialAttacker.equals(this.currentlyAttackingPlayer)) {
                  this.currentlyAttackingPlayer = null;
               }
            }
         }
      }

      if (this.currentlyAttackingPlayer != null) {
         this.setTask(new KillPlayerTask(this.currentlyAttackingPlayer));
         return 55.0F;
      } else {
         return 0.0F;
      }
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
      return "Player Defense";
   }

   static class DamageTarget {
      public TimerGame forgetInstigationTimer = new TimerGame(6.0);
      public TimerGame forgetAttackTimer = new TimerGame(30.0);
      public int timesHit = 0;
      public boolean attacking = false;

      public DamageTarget() {
         this.forgetInstigationTimer.reset();
         this.forgetAttackTimer.reset();
      }
   }
}
