package com.player2.playerengine.chains;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.entity.AbstractKillEntityTask;
import com.player2.playerengine.tasks.base.TaskChain;
import com.player2.playerengine.tasks.base.TaskRunner;
import com.player2.playerengine.automaton.api.pathing.calc.IPath;
import com.player2.playerengine.automaton.api.pathing.movement.IMovement;
import com.player2.playerengine.automaton.pathing.movement.Movement;
import com.player2.playerengine.automaton.utils.BlockStateInterface;
import java.util.Optional;

public class PreEquipItemChain extends SingleTaskChain {
   public PreEquipItemChain(TaskRunner runner) {
      super(runner);
   }

   @Override
   protected void onTaskFinish(PlayerEngineController mod) {
   }

   @Override
   public float getPriority() {
      this.update(this.controller);
      return -1.0F;
   }

   private void update(PlayerEngineController mod) {
      if (!mod.getFoodChain().isTryingToEat()) {
         TaskChain currentChain = mod.getTaskRunner().getCurrentTaskChain();
         if (currentChain != null) {
            Optional<IPath> pathOptional = mod.getBaritone().getPathingBehavior().getPath();
            if (!pathOptional.isEmpty()) {
               IPath path = pathOptional.get();
               BlockStateInterface bsi = new BlockStateInterface(this.controller.getBaritone().getEntityContext());

               for (IMovement iMovement : path.movements()) {
                  Movement movement = (Movement)iMovement;
                  if (movement.toBreak(bsi).stream().anyMatch(pos -> mod.getWorld().getBlockState(pos).getBlock().defaultDestroyTime() > 0.0F)
                     || !movement.toPlace(bsi).isEmpty()) {
                     return;
                  }
               }

               if (currentChain.getTasks().stream().anyMatch(task -> task instanceof AbstractKillEntityTask)) {
                  AbstractKillEntityTask.equipWeapon(mod);
               }
            }
         }
      }
   }

   @Override
   public String getName() {
      return "pre-equip item chain";
   }

   @Override
   public boolean isActive() {
      return true;
   }
}
