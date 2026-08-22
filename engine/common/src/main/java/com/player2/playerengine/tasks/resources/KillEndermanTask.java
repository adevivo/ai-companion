package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.tasks.ResourceTask;
import com.player2.playerengine.tasks.entity.KillEntitiesTask;
import com.player2.playerengine.tasks.entity.KillEntityTask;
import com.player2.playerengine.tasks.movement.GetWithinRangeOfBlockTask;
import com.player2.playerengine.tasks.movement.TimeoutWanderTask;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.Dimension;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.util.time.TimerGame;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class KillEndermanTask extends ResourceTask {
   private final int count;
   private final TimerGame lookDelay = new TimerGame(0.2);

   public KillEndermanTask(int count) {
      super(new ItemTarget(Items.ENDER_PEARL, count));
      this.count = count;
      this.forceDimension(Dimension.NETHER);
   }

   @Override
   protected boolean shouldAvoidPickingUp(PlayerEngineController mod) {
      return false;
   }

   @Override
   protected void onResourceStart(PlayerEngineController mod) {
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      if (!mod.getEntityTracker().entityFound(EnderMan.class)) {
         if (WorldHelper.getCurrentDimension(mod) != Dimension.NETHER) {
            return this.getToCorrectDimensionTask(mod);
         } else {
            Optional<BlockPos> nearest = mod.getBlockScanner()
               .getNearestBlock(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);
            if (nearest.isPresent()) {
               if (WorldHelper.inRangeXZ(nearest.get(), mod.getPlayer().blockPosition(), 40.0)) {
                  this.setDebugState("Waiting for endermen to spawn...");
                  return null;
               } else {
                  this.setDebugState("Getting to warped forest biome");
                  return new GetWithinRangeOfBlockTask(nearest.get(), 35);
               }
            } else {
               this.setDebugState("Warped forest biome not found");
               return new TimeoutWanderTask();
            }
         }
      } else {
         Predicate<Entity> belowNetherRoof = entityx -> WorldHelper.getCurrentDimension(mod) != Dimension.NETHER || entityx.getY() < 125.0;
         int TOO_FAR_AWAY = WorldHelper.getCurrentDimension(mod) == Dimension.NETHER ? 10 : 256;

         for (EnderMan entity : mod.getEntityTracker().getTrackedEntities(EnderMan.class)) {
            if (entity.isAlive() && belowNetherRoof.test(entity) && entity.isCreepy() && entity.position().closerThan(mod.getPlayer().position(), TOO_FAR_AWAY)
               )
             {
               return new KillEntityTask(entity);
            }
         }

         return new KillEntitiesTask(belowNetherRoof, EnderMan.class);
      }
   }

   @Override
   protected void onResourceStop(PlayerEngineController mod, Task interruptTask) {
   }

   @Override
   protected boolean isEqualResource(ResourceTask other) {
      return other instanceof KillEndermanTask task ? task.count == this.count : false;
   }

   @Override
   protected String toDebugStringName() {
      return "Hunting endermen for pearls - " + this.controller.getItemStorage().getItemCount(Items.ENDER_PEARL) + "/" + this.count;
   }
}
