package adris.altoclef.mixins.baritone;

import adris.altoclef.util.CompanionTickGuard;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerChunkCache.class})
public abstract class MixinServerChunkManager implements ServerChunkManagerAccessor {
   @Shadow
   @Nullable
   protected abstract ChunkHolder getVisibleChunkIfPresent(long var1);

   @Shadow
   @Final
   ServerLevel level;

   @Unique
   private Holder<Biome> aicompanion$fallbackBiome;

   @Nullable
   @Override
   public LevelChunk automatone$getChunkNow(int chunkX, int chunkZ) {
      ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
      return chunkHolder == null ? null : chunkHolder.getTickingChunk();
   }

   /**
    * While a companion AI tick is running, answer chunk reads from what is already in memory and hand
    * back an empty chunk otherwise — never block.
    *
    * <p>Vanilla's contract here is "load the chunk, however long that takes", and honouring it from the
    * server thread is a self-deadlock: the load can only be executed by the very thread now parked
    * waiting for it. The task engine reads blocks at arbitrary remembered positions from inside the
    * tick, and any of those positions may legitimately be unloaded.
    *
    * <p>The worst form of this was a read reaching a <em>stopped</em> server's chunk cache, whose
    * future nothing will ever complete — see {@code BaritoneComponents}, which fixes that cause at
    * source. This mixin remains the backstop for the ordinary case.
    *
    * <p>The companion consequently sees unloaded terrain as air, which is exactly how Baritone's
    * {@code BlockStateInterface} already presents it — pathing treats it as unknown and re-plans once
    * the chunk really arrives.
    *
    * <p>Scope is deliberately narrow: {@link CompanionTickGuard} is a thread-local set only around the
    * companion's own tick, so vanilla, other mods, and worker threads are untouched.
    */
   @Inject(
      method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
      at = @At("HEAD"),
      cancellable = true
   )
   private void aicompanion$neverBlockDuringCompanionTick(
         int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
      if (!CompanionTickGuard.isActive()) {
         return;
      }
      LevelChunk present = this.automatone$getChunkNow(x, z);
      cir.setReturnValue(present != null ? present : this.aicompanion$emptyChunk(x, z));
   }

   @Unique
   private ChunkAccess aicompanion$emptyChunk(int x, int z) {
      if (this.aicompanion$fallbackBiome == null) {
         this.aicompanion$fallbackBiome =
               this.level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
      }
      return new EmptyLevelChunk(this.level, new ChunkPos(x, z), this.aicompanion$fallbackBiome);
   }
}
