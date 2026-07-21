package adris.altoclef.player2api;

import baritone.utils.DirUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChatclefConfigPersistantState {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = DirUtil.getConfigDir().resolve("chatclef_config.json");
   private static ChatclefConfigPersistantState config = load();
   private boolean sttHintEnabled = true;

   public static boolean isSttHintEnabled() {
      return instance().sttHintEnabled;
   }

   public static void updateSttHint(boolean value) {
      System.out.println("[ChatclefConfigPersistantState]: updateSttHint called with: " + value);
      instance().sttHintEnabled = value;
      save();
   }

   private static ChatclefConfigPersistantState load() {
      if (Files.exists(CONFIG_PATH)) {
         try {
            String json = Files.readString(CONFIG_PATH);
            System.out.println("[ChatclefConfigPersistantState]: Reading from file...");
            return (ChatclefConfigPersistantState)GSON.fromJson(json, ChatclefConfigPersistantState.class);
         } catch (IOException var1) {
            var1.printStackTrace();
         }
      }

      System.out.println("[ChatclefConfigPersistantState]: Could not load file, using default.");
      return new ChatclefConfigPersistantState();
   }

   private static void save() {
      System.out.println("[ChatclefConfigPersistantState]: save() called");

      try {
         Files.writeString(CONFIG_PATH, GSON.toJson(config));
         System.out.println("[ChatclefConfigPersistantState]: Writing to file...");
      } catch (IOException var1) {
         System.err.println("[ChatclefConfigPersistantState]: Writing to file FAILED");
         var1.printStackTrace();
      }
   }

   private static ChatclefConfigPersistantState instance() {
      return config;
   }
}
