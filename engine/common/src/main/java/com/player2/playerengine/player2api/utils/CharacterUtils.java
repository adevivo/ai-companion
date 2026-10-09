package com.player2.playerengine.player2api.utils;

import com.player2.playerengine.player2api.Character;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public class CharacterUtils {
   public static Character DEFAULT_CHARACTER = new Character(
      "AI agent", "AI", "Greetings", "You are a helpful AI Agent", "minecraft:textures/entity/player/wide/steve.png", new String[0], ""
   );

   public static Character readFromBuf(FriendlyByteBuf buf) {
      String name = buf.readUtf();
      String shortName = buf.readUtf();
      String greetingInfo = buf.readUtf();
      String description = buf.readUtf();
      String skinURL = buf.readUtf();
      int arrSize = buf.readInt();
      String[] voiceIds = new String[arrSize];

      for (int i = 0; i < arrSize; i++) {
         voiceIds[i] = buf.readUtf();
      }

      String persona = buf.readUtf();
      return new Character(name, shortName, greetingInfo, description, skinURL, voiceIds, persona);
   }

   public static void writeToBuf(FriendlyByteBuf buf, Character character) {
      buf.writeUtf(character.name());
      buf.writeUtf(character.shortName());
      buf.writeUtf(character.greetingInfo());
      buf.writeUtf(character.description());
      buf.writeUtf(character.skinURL());
      buf.writeInt(character.voiceIds().length);

      for (String id : character.voiceIds()) {
         buf.writeUtf(id);
      }

      buf.writeUtf(character.persona());
   }

   public static Character readFromNBT(CompoundTag compound) {
      String name = compound.getStringOr("name", "");
      String shortName = compound.getStringOr("shortName", "");
      String greetingInfo = compound.getStringOr("greetingInfo", "");
      String description = compound.getStringOr("description", "");
      String skinURL = compound.getStringOr("skinURL", "");
      ListTag voiceIdsList = compound.getListOrEmpty("voiceIds");
      String[] voiceIds = new String[voiceIdsList.size()];

      for (int i = 0; i < voiceIdsList.size(); i++) {
         voiceIds[i] = voiceIdsList.getStringOr(i, "");
      }

      String persona = compound.getStringOr("persona", "");
      return new Character(name, shortName, greetingInfo, description, skinURL, voiceIds, persona);
   }

   public static void writeToNBT(CompoundTag compound, Character character) {
      compound.putString("name", character.name());
      compound.putString("shortName", character.shortName());
      compound.putString("greetingInfo", character.greetingInfo());
      compound.putString("description", character.description());
      compound.putString("skinURL", character.skinURL());
      ListTag voiceIds = new ListTag();

      for (String id : character.voiceIds()) {
         voiceIds.add(StringTag.valueOf(id));
      }

      compound.put("voiceIds", voiceIds);
      compound.putString("persona", character.persona());
   }
}