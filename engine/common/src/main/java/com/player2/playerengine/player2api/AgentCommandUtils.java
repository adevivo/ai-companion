package com.player2.playerengine.player2api;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.util.ItemTarget;
import java.util.ArrayList;
import java.util.List;

public class AgentCommandUtils {
   public static ItemTarget[] addPresentItemsToTargets(PlayerEngineController mod, ItemTarget[] items) {
      List<ItemTarget> resultTargets = new ArrayList<>();

      for (ItemTarget target : items) {
         int count = target.getTargetCount();
         count += mod.getItemStorage().getItemCountInventoryOnly(target.getMatches());
         resultTargets.add(new ItemTarget(target, count));
      }

      return resultTargets.toArray(new ItemTarget[0]);
   }
}
