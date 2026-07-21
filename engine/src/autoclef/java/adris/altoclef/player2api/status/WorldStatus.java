package adris.altoclef.player2api.status;

import adris.altoclef.AltoClefController;

public class WorldStatus extends ObjectStatus {
   public static WorldStatus fromMod(AltoClefController mod) {
      return (WorldStatus)new WorldStatus()
         .add("weather", StatusUtils.getWeatherString(mod))
         .add("dimension", StatusUtils.getDimensionString(mod))
         .add("spawn position", StatusUtils.getSpawnPosString(mod))
         .add("nearby blocks", StatusUtils.getNearbyBlocksString(mod))
         .add("nearby hostiles", StatusUtils.getNearbyHostileMobs(mod))
         .add("nearby players", StatusUtils.getNearbyPlayers(mod))
         .add("nearby other npcs", StatusUtils.getNearbyNPCs(mod))
         .add("difficulty", StatusUtils.getDifficulty(mod))
         .add("timeInfo", StatusUtils.getTimeString(mod));
   }
}
