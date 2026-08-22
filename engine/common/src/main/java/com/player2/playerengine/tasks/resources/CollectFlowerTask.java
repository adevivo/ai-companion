package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;

public class CollectFlowerTask extends MineAndCollectTask {
   public CollectFlowerTask(int count) {
      super(new ItemTarget(ItemHelper.FLOWER, count), ItemHelper.itemsToBlocks(ItemHelper.FLOWER), MiningRequirement.HAND);
   }
}
