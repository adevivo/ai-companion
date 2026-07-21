package adris.altoclef.util.helpers;

import adris.altoclef.Debug;
import adris.altoclef.util.serialization.BlockPosDeserializer;
import adris.altoclef.util.serialization.BlockPosSerializer;
import adris.altoclef.util.serialization.ChunkPosDeserializer;
import adris.altoclef.util.serialization.ChunkPosSerializer;
import adris.altoclef.util.serialization.IFailableConfigFile;
import adris.altoclef.util.serialization.IListConfigFile;
import adris.altoclef.util.serialization.Vec3dDeserializer;
import adris.altoclef.util.serialization.Vec3dSerializer;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public class ConfigHelper {
   private static final String ALTO_FOLDER = "altoclef";
   private static final HashMap<String, Runnable> loadedConfigs = new HashMap<>();

   private static File getConfigFile(String path) {
      String fullPath = "altoclef" + File.separator + path;
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
      } else {
         ObjectMapper mapper = new ObjectMapper();
         SimpleModule module = new SimpleModule();
         module.addDeserializer(Vec3.class, new Vec3dDeserializer());
         module.addDeserializer(ChunkPos.class, new ChunkPosDeserializer());
         module.addDeserializer(BlockPos.class, new BlockPosDeserializer());
         mapper.registerModule(module);

         try {
            result = mapper.readValue(loadFrom, classToLoad);
         } catch (JsonMappingException var9) {
            Debug.logError(
               "Failed to parse Config file of type "
                  + classToLoad.getSimpleName()
                  + "at "
                  + path
                  + ". JSON Error Message: "
                  + var9.getMessage()
                  + ".\n JSON Error STACK TRACE:\n\n"
            );
            var9.printStackTrace();
            if (result instanceof IFailableConfigFile failable) {
               failable.failedToLoad();
            }

            return result;
         } catch (IOException var10) {
            Debug.logError("Failed to read Config at " + path + ".");
            var10.printStackTrace();
            if (result instanceof IFailableConfigFile failable) {
               failable.failedToLoad();
            }

            return result;
         }

         saveConfig(path, result);
         return result;
      }
   }

   public static <T> void loadConfig(String path, Supplier<T> getDefault, Class<T> classToLoad, Consumer<T> onReload) {
      T config = getConfig(path, getDefault, classToLoad);
      loadedConfigs.put(path, () -> onReload.accept(config));
      onReload.accept(config);
   }

   public static <T> void saveConfig(String path, T config) {
      ObjectMapper mapper = new ObjectMapper();
      SimpleModule module = new SimpleModule();
      module.addSerializer(Vec3.class, new Vec3dSerializer());
      module.addSerializer(BlockPos.class, new BlockPosSerializer());
      module.addSerializer(ChunkPos.class, new ChunkPosSerializer());
      mapper.registerModule(module);
      File configFile = getConfigFile(path);
      createParentDirectories(configFile);

      try {
         enablePrettyPrinting(mapper);
         writeConfigToFile(mapper, configFile, config);
      } catch (IOException var6) {
         handleIOException(var6);
      }
   }

   private static void createParentDirectories(File file) {
      try {
         Path parentPath = file.getParentFile().toPath();
         Files.createDirectories(parentPath);
      } catch (IOException var2) {
         System.err.println("Failed to create parent directories: " + var2.getMessage());
      }
   }

   private static void enablePrettyPrinting(ObjectMapper mapper) {
      if (mapper != null) {
         mapper.enable(SerializationFeature.INDENT_OUTPUT);
         DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
         prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
         mapper.writer(prettyPrinter);
      }
   }

   private static <T> void writeConfigToFile(ObjectMapper objectMapper, File configFile, T configData) throws IOException {
      Writer writer = new FileWriter(configFile);

      try {
         objectMapper.writeValue(writer, configData);
         writer.close();
      } catch (Throwable var7) {
         try {
            writer.close();
         } catch (Throwable var6) {
            var7.addSuppressed(var6);
         }

         throw var7;
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
