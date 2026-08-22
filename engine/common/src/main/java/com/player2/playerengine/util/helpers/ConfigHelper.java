package com.player2.playerengine.util.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.util.Debug;
import com.player2.playerengine.util.serialization.IFailableConfigFile;
import com.player2.playerengine.util.serialization.IListConfigFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.player2.playerengine.util.serialization.gson.BlockPosTypeAdapter;
import com.player2.playerengine.util.serialization.gson.ChunkPosTypeAdapter;
import com.player2.playerengine.util.serialization.gson.ItemListTypeAdapter;
import com.player2.playerengine.util.serialization.gson.Vec3dTypeAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class ConfigHelper {
   private static final HashMap<String, Runnable> loadedConfigs = new HashMap<>();

   private static final Gson GSON = new GsonBuilder()
           .setPrettyPrinting()
           .registerTypeAdapter(BlockPos.class, new BlockPosTypeAdapter())
           .registerTypeAdapter(ChunkPos.class, new ChunkPosTypeAdapter())
           .registerTypeAdapter(Vec3.class, new Vec3dTypeAdapter())
           .registerTypeAdapter(new TypeToken<List<Item>>() {}.getType(), new ItemListTypeAdapter())
           .create();

   private static File getConfigFile(String path) {
      String fullPath = PlayerEngine.MOD_ID + File.separator + path;
      return new File(fullPath);
   }

   public static void reloadAllConfigs() {
      for (Runnable config : loadedConfigs.values()) {
         config.run();
      }
   }

   private static <T> T getConfig(String path, Supplier<T> getDefault, Class<T> classToLoad) {
      T result = getDefault.get();
      File loadFrom = getConfigFile(path);

      if (!loadFrom.exists()) {
         saveConfig(path, result);
         return result;
      }

      try (FileReader reader = new FileReader(loadFrom)) {
         result = GSON.fromJson(reader, classToLoad);
      } catch (JsonSyntaxException e) {
         Debug.logError(
                 "Failed to parse Config file of type "
                         + classToLoad.getSimpleName()
                         + " at "
                         + path
                         + ". JSON Error Message: "
                         + e.getMessage()
         );
         e.printStackTrace();
         if (result instanceof IFailableConfigFile failable) {
            failable.onFailLoad();
         }
         return result;
      } catch (IOException e) {
         Debug.logError("Failed to read Config at " + path + ".");
         e.printStackTrace();
         if (result instanceof IFailableConfigFile failable) {
            failable.onFailLoad();
         }
         return result;
      }

      saveConfig(path, result);
      return result;
   }

   public static <T> void saveConfig(String path, T config) {
      File configFile = getConfigFile(path);
      createParentDirectories(configFile);

      try (FileWriter writer = new FileWriter(configFile)) {
         GSON.toJson(config, writer);
      } catch (IOException e) {
         handleIOException(e);
      }
   }

   public static <T> void loadConfig(String path, Supplier<T> getDefault, Class<T> classToLoad, Consumer<T> onReload) {
      T config = getConfig(path, getDefault, classToLoad);
      loadedConfigs.put(path, () -> onReload.accept(config));
      onReload.accept(config);
   }

   private static void createParentDirectories(File file) {
      try {
         Path parentPath = file.getParentFile().toPath();
         Files.createDirectories(parentPath);
      } catch (IOException var2) {
         System.err.println("Failed to create parent directories: " + var2.getMessage());
      }
   }

   private static void handleIOException(IOException exception) {
      String errorMessage = "An IOException occurred: " + exception.getMessage();
      System.err.println(errorMessage);
   }

   private static <T extends IListConfigFile> T getListConfig(String path, Supplier<T> getDefault) {
      IListConfigFile iListConfigFile = getDefault.get();
      iListConfigFile.onLoadStart();
      File configFile = getConfigFile(path);
      if (!configFile.exists()) {
         return (T)iListConfigFile;
      } else {
         try {
            FileInputStream fis = new FileInputStream(configFile);

            try {
               Scanner scanner = new Scanner(fis);

               try {
                  while (scanner.hasNextLine()) {
                     String line = trimComment(scanner.nextLine()).trim();
                     if (!line.isEmpty()) {
                        iListConfigFile.addLine(line);
                     }
                  }

                  scanner.close();
               } catch (Throwable var10) {
                  try {
                     scanner.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }

                  throw var10;
               }

               fis.close();
               return (T)iListConfigFile;
            } catch (Throwable var11) {
               try {
                  fis.close();
               } catch (Throwable var8) {
                  var11.addSuppressed(var8);
               }

               throw var11;
            }
         } catch (IOException var12) {
            var12.printStackTrace();
            return null;
         }
      }
   }

   public static <T extends IListConfigFile> void loadListConfig(String path, Supplier<T> getDefault, Consumer<T> onReload) {
      T result = getListConfig(path, getDefault);
      loadedConfigs.put(path, () -> onReload.accept(result));
      onReload.accept(result);
   }

   private static String trimComment(String line) {
      int poundIndex = line.indexOf(35);
      return poundIndex == -1 ? line : line.substring(0, poundIndex);
   }

   public static void ensureCommentedListFileExists(String path, String startingComment) {
      File configFile = getConfigFile(path);
      if (!configFile.exists()) {
         StringBuilder commentBuilder = new StringBuilder();

         for (String line : startingComment.split("\\r?\\n")) {
            if (!line.isEmpty()) {
               commentBuilder.append("# ").append(line).append("\n");
            }
         }

         try {
            Files.write(configFile.toPath(), commentBuilder.toString().getBytes());
         } catch (IOException var8) {
            handleException(var8);
         }
      }
   }

   private static void handleException(IOException exception) {
      System.err.println("An error occurred: " + exception.getMessage());
   }
}
