package com.player2.playerengine.tasks.resources;

import com.player2.playerengine.util.ItemTarget;
import com.player2.playerengine.util.MiningRequirement;
import com.player2.playerengine.util.helpers.ItemHelper;

public class CollectSaplingsTask extends MineAndCollectTask {
   public CollectSaplingsTask(int count) {
      super(new ItemTarget(ItemHelper.SAPLINGS, count), ItemHelper.SAPLING_SOURCES, MiningRequirement.HAND);
   }
}
