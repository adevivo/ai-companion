package adris.altoclef.player2api.status;

import adris.altoclef.AltoClefController;
import net.minecraft.world.entity.LivingEntity;

public class AgentStatus extends ObjectStatus {
   public static AgentStatus fromMod(AltoClefController mod) {
      LivingEntity player = mod.getPlayer();
      return (AgentStatus) new AgentStatus()
            .add("position", StatusUtils.getCurrentPosition(mod))
            .add("groundLevel", StatusUtils.getGroundLevelString(mod))
            .add("health", String.format("%.2f/20", player.getHealth()))
            .add("food",
                  String.format("%.2f/20", (float) mod.getBaritone().getEntityContext().hungerManager().getFoodLevel()))
            .add("saturation",
                  String.format("%.2f/20", mod.getBaritone().getEntityContext().hungerManager().getSaturationLevel()))
            .add("healing", healingStatus(mod, player))
            .add("inventory", StatusUtils.getInventoryString(mod))
            .add("heldItem", StatusUtils.getHeldItemString(mod))
            .add("taskStatus", StatusUtils.getTaskStatusString(mod))
            .add("oxygenLevel", StatusUtils.getOxygenString(mod))
            .add("armor", StatusUtils.getEquippedArmorStatusString(mod))
            .add("gamemode", StatusUtils.getGamemodeString(mod));
      // .add("taskTree", StatusUtils.getTaskTree(mod));
   }

   /** Food level at or above which natural regeneration happens at all. Vanilla's number. */
   private static final int REGEN_FOOD_THRESHOLD = 18;

   /**
    * Whether the companion is currently healing, and if not, why not.
    *
    * <p>Health and food are both already in the status, but the <em>relationship</em> between them is
    * not something the model can be expected to infer: below 18 food nothing regenerates at all, so a
    * hurt and hungry companion sits at the same health indefinitely and has no way to work out that
    * eating is what unblocks it. Left to itself it reads "I am injured" and goes looking for a fight to
    * avoid or an owner to tell, rather than reaching for the food in its own pack.
    *
    * <p>Spelled out as a sentence rather than a number because that is what actually changes the
    * model's next move.
    */
   private static String healingStatus(AltoClefController mod, LivingEntity player) {
      int food = mod.getBaritone().getEntityContext().hungerManager().getFoodLevel();
      if (player.getHealth() >= player.getMaxHealth()) {
         return food >= REGEN_FOOD_THRESHOLD
               ? "at full health"
               : "at full health, but too hungry to regenerate if you get hurt (need "
                     + REGEN_FOOD_THRESHOLD + "+ food) — eat now, before a fight rather than during one";
      }
      if (food < REGEN_FOOD_THRESHOLD) {
         return "NOT healing: you are hurt and will not regenerate at all below " + REGEN_FOOD_THRESHOLD
               + " food. Eat something and healing resumes on its own. Use `eat`.";
      }
      return "healing gradually (well fed)";
   }
}
