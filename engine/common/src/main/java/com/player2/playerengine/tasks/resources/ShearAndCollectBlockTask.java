package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.BotBehaviour;
import com.player2.playerengine.TaskCatalogue;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class ShearAndCollectBlockTask extends MineAndCollectTask {
   public ShearAndCollectBlockTask(ItemTarget[] itemTargets, Block... blocksToMine) {
      super(itemTargets, blocksToMine, MiningRequirement.HAND);
   }

   public ShearAndCollectBlockTask(Item[] items, int count, Block... blocksToMine) {
      this(new ItemTarget[]{new ItemTarget(items, count)}, blocksToMine);
   }

   public ShearAndCollectBlockTask(Item item, int count, Block... blocksToMine) {
      this(new Item[]{item}, count, blocksToMine);
   }

   @Override
   protected void onStart() {
      BotBehaviour botBehaviour = this.controller.getBehaviour();
      botBehaviour.push();
      botBehaviour.forceUseTool((blockState, itemStack) -> itemStack.getItem() == Items.SHEARS && ItemHelper.areShearsEffective(blockState.getBlock()));
      super.onStart();
   }

   @Override
   protected void onStop(Task interruptTask) {
      this.controller.getBehaviour().pop();
      super.onStop(interruptTask);
   }

   @Override
   protected Task onResourceTick(PlayerEngineController mod) {
      return (Task)(!mod.getItemStorage().hasItem(Items.SHEARS) ? TaskCatalogue.getItemTask(Items.SHEARS, 1) : super.onResourceTick(mod));
   }
}
