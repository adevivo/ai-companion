package baritone;

import baritone.command.defaults.DefaultCommands;
import baritone.entity.CustomFishingBobberEntity;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@KeepName
public final class PlayerEngine implements ModInitializer {
   public static final Logger LOGGER = LogManager.getLogger("Automatone");
   public static final String MOD_ID = "automatone";
   public static final TagKey<Item> EMPTY_BUCKETS = TagKey.create(Registries.ITEM, id("empty_buckets"));
   public static final TagKey<Item> WATER_BUCKETS = TagKey.create(Registries.ITEM, id("water_buckets"));
   private static final ThreadPoolExecutor threadPool;
   public static final EntityType<CustomFishingBobberEntity> FISHING_BOBBER = FabricEntityTypeBuilder.<CustomFishingBobberEntity>create()
      .spawnGroup(MobCategory.MISC)
      .entityFactory(CustomFishingBobberEntity::new)
      .dimensions(EntityDimensions.scalable(EntityType.FISHING_BOBBER.getWidth(), EntityType.FISHING_BOBBER.getHeight()))
      .trackRangeBlocks(64)
      .trackedUpdateRate(1)
      .forceTrackedVelocityUpdates(true)
      .build();

   public static ResourceLocation id(String path) {
      return new ResourceLocation("automatone", path);
   }

   public static ThreadPoolExecutor getExecutor() {
      return threadPool;
   }

   public void onInitialize() {
      DefaultCommands.registerAll();
      Registry.register(BuiltInRegistries.ENTITY_TYPE, id("fishing_bobber"), FISHING_BOBBER);
   }

   static {
      AtomicInteger threadCounter = new AtomicInteger(0);
      threadPool = new ThreadPoolExecutor(
         4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> new Thread(r, "Automatone Worker " + threadCounter.incrementAndGet())
      );
   }
}
