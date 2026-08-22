package com.player2.playerengine.player2api.status;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.manager.ConversationManager;
import com.player2.playerengine.tasks.base.Task;
import com.player2.playerengine.util.helpers.ItemHelper;
import com.player2.playerengine.util.helpers.WorldHelper;
import com.player2.playerengine.automaton.api.entity.IAutomatone;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

public class StatusUtils {
   /**
    * Radius, in blocks, at which a companion notices another companion. Deliberately shorter than
    * {@link ConversationManager#messagePassingMaxDistance} (earshot, 64): a companion may hear one
    * it cannot see, and that asymmetry is intended.
    */
   private static final int NEARBY_NPC_RADIUS = 32;

   public static String getInventoryString(PlayerEngineController mod) {
      Map<String, Integer> counts = new HashMap<>();

      for (int i = 0; i < mod.getBaritone().getEntityContext().inventory().getContainerSize(); i++) {
         ItemStack stack = mod.getBaritone().getEntityContext().inventory().getItem(i);
         if (!stack.isEmpty()) {
            String name = ItemHelper.stripItemName(stack.getItem());
            counts.put(name, counts.getOrDefault(name, 0) + stack.getCount());
         }
      }

      ObjectStatus status = new ObjectStatus();

      for (Entry<String, Integer> entry : counts.entrySet()) {
         status.add(entry.getKey(), entry.getValue().toString());
      }

      return status.toString();
   }

   public static String getDimensionString(PlayerEngineController mod) {
      return mod.getWorld().dimension().identifier().toString().replace("minecraft:", "");
   }

   public static String getWeatherString(PlayerEngineController mod) {
      boolean isRaining = mod.getWorld().isRaining();
      boolean isThundering = mod.getWorld().isThundering();
      ObjectStatus status = new ObjectStatus().add("isRaining", String.valueOf(isRaining)).add("isThundering",
            String.valueOf(isThundering));
      return status.toString();
   }

   public static String getSpawnPosString(PlayerEngineController mod) {
      BlockPos spawnPos = mod.getWorld().getRespawnData().globalPos().pos();
      return String.format("(%d, %d, %d)", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
   }

   public static String getTaskStatusString(PlayerEngineController mod) {
      String noTask = "No tasks currently running.";
      List<Task> tasks = mod.getUserTaskChain().getTasks();
      // ignore lookATOwner task
      return tasks.isEmpty() ? noTask
            : tasks.get(0).toString().contains("LookAtOwner") ? noTask : tasks.get(0).toString();
   }

   public static String getNearbyBlocksString(PlayerEngineController mod) {
      int radius = 12;
      BlockPos center = mod.getPlayer().blockPosition();
      Map<String, Integer> blockCounts = new HashMap<>();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos pos = center.offset(dx, dy, dz);
               String blockName = mod.getWorld().getBlockState(pos).getBlock().getDescriptionId()
                     .replace("block.minecraft.", "");
               if (!blockName.equals("air")) {
                  blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);
               }
            }
         }
      }

      ObjectStatus status = new ObjectStatus();

      for (Entry<String, Integer> entry : blockCounts.entrySet()) {
         status.add(entry.getKey(), entry.getValue().toString());
      }

      return status.toString();
   }

   public static String getOxygenString(PlayerEngineController mod) {
      return String.format("%s/300", mod.getPlayer().getAirSupply());
   }

   public static String getNearbyHostileMobs(PlayerEngineController mod) {
      int radius = 32;
      List<String> descriptions = new ArrayList<>();

      for (Entity entity : mod.getWorld().getAllEntities()) {
         if (entity instanceof Monster && entity.distanceTo(mod.getPlayer()) < radius) {
            String type = entity.getType().getDescriptionId();
            String niceName = type.replace("entity.minecraft.", "");
            String position = entity.position().align(EnumSet.allOf(Axis.class)).toString();
            descriptions.add(niceName + " at " + position);
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby hostile mobs within %d", radius)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static String getEquippedArmorStatusString(PlayerEngineController mod) {
      LivingEntity player = mod.getPlayer();
      ObjectStatus status = new ObjectStatus();
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
      ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
      ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
      status.add("helmet",
            !head.isEmpty() && head.is(ItemTags.ARMOR_ENCHANTABLE)
                  ? head.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add(
            "chestplate",
            !chest.isEmpty() && chest.is(ItemTags.ARMOR_ENCHANTABLE)
                  ? chest.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add("leggings",
            !legs.isEmpty() && legs.is(ItemTags.ARMOR_ENCHANTABLE)
                  ? legs.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add("boots",
            !feet.isEmpty() && feet.is(ItemTags.ARMOR_ENCHANTABLE)
                  ? feet.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      status.add(
            "offhand_shield",
            !offhand.isEmpty() && offhand.getItem() instanceof ShieldItem
                  ? offhand.getItem().getDescriptionId().replace("item.minecraft.", "")
                  : "none");
      return status.toString();
   }

   /** What the agent is currently holding in its main hand — so the LLM can perceive/confirm equips. */
   public static String getHeldItemString(PlayerEngineController mod) {
      ItemStack main = mod.getPlayer().getItemBySlot(EquipmentSlot.MAINHAND);
      return main.isEmpty() ? "empty hand" : ItemHelper.stripItemName(main.getItem());
   }

   public static String getNearbyPlayers(PlayerEngineController mod) {
      List<String> descriptions = new ArrayList<>();
      UUID self = mod.getPlayer().getUUID();

      // Scans the level's player list directly. It must not read EntityTracker.getCloseEntities():
      // that list is gated on PlayerExtraController.inRange, i.e. Settings.entityReachRange, which is
      // 3 blocks of melee reach -- so this field claimed 64 and delivered 3, and a companion was told
      // it was alone by the person standing in front of it.
      for (Player player : mod.getWorld().players()) {
         if (!player.getUUID().equals(self)
               && player.distanceTo(mod.getPlayer()) < ConversationManager.messagePassingMaxDistance) {
            String username = player.getName().getString();
            String position = player.position().align(EnumSet.allOf(Axis.class)).toString();
            descriptions.add(username + " at " + position);
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby users within %.2f", ConversationManager.messagePassingMaxDistance)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static String getNearbyNPCs(PlayerEngineController mod) {
      List<String> descriptions = new ArrayList<>();
      UUID self = mod.getPlayer().getUUID();

      // Same world scan as getNearbyHostileMobs above, and for the same reason as getNearbyPlayers.
      // Self-exclusion is by UUID, never by display name: two companions sharing a name used to
      // erase each other from this field.
      for (Entity entity : mod.getWorld().getAllEntities()) {
         if (entity instanceof IAutomatone
               && !entity.getUUID().equals(self)
               && entity.distanceTo(mod.getPlayer()) < NEARBY_NPC_RADIUS) {
            String username = entity.getDisplayName().getString();
            String position = entity.position().align(EnumSet.allOf(Axis.class)).toString();
            descriptions.add(username + " at " + position);
         }
      }

      return descriptions.isEmpty()
            ? String.format("no nearby npcs within %d", NEARBY_NPC_RADIUS)
            : "[" + String.join(",", descriptions.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]";
   }

   public static float getUserNameDistance(PlayerEngineController mod, String targetUsername) {
      for (Player player : mod.getWorld().players()) {
         String username = player.getName().getString();
         if (username.equals(targetUsername)) {
            return player.distanceTo(mod.getPlayer());
         }
      }

      return Float.MAX_VALUE;
   }

   public static String getDifficulty(PlayerEngineController mod) {
      return mod.getWorld().getDifficulty().toString();
   }

   public static String getTimeString(PlayerEngineController mod) {
      ObjectStatus status = new ObjectStatus();
      status.add("isDay", Boolean.toString(!mod.getWorld().isDarkOutside()));
      status.add("timeOfDay", String.format("%d/24,000", mod.getWorld().getDayTime() % 24000L));
      return status.toString();
   }

   public static String getGamemodeString(PlayerEngineController mod) {
      return mod.getInteractionManager().getGameType().isCreative() ? "creative" : "survival";
   }

   /**
    * The bot's FEET position — the block it occupies, not where it is looking from.
    *
    * <p>This used to report {@code getEyePosition()}, roughly 1.53 blocks higher (the companion is
    * player-sized, {@code height * 0.85}), while every prompt that consumes it — notably the
    * build_structure ground-level rule — describes it as the feet. A build placed at the reported Y
    * therefore floated a block or two above the terrain even when the model followed instructions
    * exactly. Only {@link AgentStatus} reads this; the raytracing/look code calls
    * {@code getEyePosition()} directly and is unaffected.
    */
   /**
    * The bot's position as whole block coordinates, in the form a command takes.
    *
    * <p>Was {@code Vec3.toString()}, which renders "(11.902638855274441, 70.0, -46.66562711550304)".
    * Models copy this field straight into {@code goto}, and that string is not valid argument syntax
    * — one observed session spent 39 consecutive turns re-issuing it and failing. The precision was
    * useless to a block-based agent and cost tokens in every prompt besides.
    */
   public static String getCurrentPosition(PlayerEngineController mod) {
      net.minecraft.world.phys.Vec3 pos = mod.getEntity().position();
      return String.format("%d %d %d",
            (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
   }

   /**
    * Y of the ground block the bot is standing on, so "ground level" is something it can read rather
    * than guess. Without it a model asked to build at ground level has no terrain height anywhere in
    * its context and will reach for any plausible-looking Y nearby — in one observed session it took
    * the Y of a skeleton in {@code nearby hostiles} and buried the build two blocks down.
    *
    * <p>Answered from the block the entity is actually resting on, because the prompt promises this is
    * "the ground block you are standing on". Sampling a column at the rounded centre broke that promise
    * twice over: underground it read the terrain overhead (in a cave at y=42 it reported 70), and on
    * the edge of a structure it read straight past the block underfoot — measured 2026-07-29 standing
    * on a roof at y=69 over water, where it reported 62.
    *
    * <p>{@code mainSupportingBlockPos}, which {@link Entity#getOnPos()} returns, is maintained by the
    * engine from the collision that actually holds the entity up, so it is right on a one-block ledge
    * where no single column is. It only exists while standing; airborne, fall back to the column read.
    */
   public static String getGroundLevelString(PlayerEngineController mod) {
      Entity entity = mod.getEntity();
      if (entity.onGround()) {
         return Integer.toString(entity.getOnPos().getY());
      }
      int ground = WorldHelper.groundYNear(mod, Mth.floor(entity.getX()), Mth.floor(entity.getZ()),
            Mth.floor(entity.getY()));
      return Integer.toString(ground);
   }

   public static String getTaskTree(PlayerEngineController mod) {
      Task task = mod.getUserTaskChain().getCurrentTask();
      return task == null ? "Task tree is empty" : task.getTaskTree();
   }

   public static float getDistanceToUUID(PlayerEngineController mod, UUID target) {
      // for (Player player : mod.getWorld().players()) {
      // if (player.getUUID().equals(target)) {
      // return player.distanceTo(mod.getPlayer());
      // }
      // }
      for (Entity entity : mod.getWorld().getAllEntities()) {
         if (entity.getUUID().equals(target)) {
            return entity.distanceTo(mod.getPlayer());
         }
      }

      return Float.MAX_VALUE;
   }

   public static float getDistanceToUsername(PlayerEngineController mod, String username) {
      return mod.getWorld().players().stream()
            .filter(p -> p.getName().getString().equals(username))
            .findFirst()
            .map(p -> p.distanceTo(mod.getPlayer()))
            .orElse(Float.MAX_VALUE);
   }
}
