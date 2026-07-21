package baritone.utils.player;

import baritone.api.BaritoneAPI;
import baritone.api.cache.IWorldData;
import baritone.api.entity.IHungerManagerProvider;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityHungerManager;
import baritone.api.entity.LivingEntityInventory;
import baritone.api.pathing.calc.Avoidance;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IEntityContext;
import baritone.api.utils.IInteractionController;
import baritone.api.utils.RayTraceUtils;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class EntityContext implements IEntityContext {
   private final LivingEntity entity;
   @Nullable
   private Supplier<List<Avoidance>> avoidanceFinder;

   public EntityContext(LivingEntity entity) {
      this.entity = entity;
   }

   @Override
   public LivingEntity entity() {
      return this.entity;
   }

   @Nullable
   @Override
   public LivingEntityInventory inventory() {
      return this.entity instanceof IInventoryProvider ? ((IInventoryProvider)this.entity).getLivingInventory() : null;
   }

   @Nullable
   @Override
   public LivingEntityHungerManager hungerManager() {
      return this.entity instanceof IHungerManagerProvider ? ((IHungerManagerProvider)this.entity).getHungerManager() : null;
   }

   @Override
   public IInteractionController playerController() {
      return IInteractionController.KEY.get(this.entity);
   }

   @Override
   public ServerLevel world() {
      Level world = this.entity.level();
      if (world.isClientSide) {
         throw new IllegalStateException();
      } else {
         return (ServerLevel)world;
      }
   }

   @Override
   public IWorldData worldData() {
      return BaritoneAPI.getProvider().getBaritone(this.entity).getEntityContext().worldData();
   }

   @Override
   public HitResult objectMouseOver() {
      return RayTraceUtils.rayTraceTowards(this.entity(), this.entityRotations(), this.playerController().getBlockReachDistance());
   }

   @Override
   public BetterBlockPos feetPos() {
      double x = this.entity().getX();
      double z = this.entity().getZ();
      BetterBlockPos feet = new BetterBlockPos(x, this.entity().getY() + 0.1251, z);
      ServerLevel world = this.world();
      if (world != null) {
         LevelChunk chunk = ((ServerChunkManagerAccessor)world.getChunkSource()).automatone$getChunkNow((int)x << 4, (int)z << 4);
         if (chunk != null && chunk.getBlockState(feet).getBlock() instanceof SlabBlock) {
            return feet.up();
         }
      }

      return feet;
   }

   private Stream<Entity> streamHostileEntities() {
      return this.worldEntitiesStream()
         .filter(entity -> entity instanceof Mob)
         .filter(entity -> !(entity instanceof Spider) || entity.getLightLevelDependentMagicValue() < 0.5)
         .filter(entity -> !(entity instanceof ZombifiedPiglin) || ((ZombifiedPiglin)entity).getLastHurtByMob() != null)
         .filter(entity -> !(entity instanceof EnderMan) || ((EnderMan)entity).isCreepy());
   }

   @Override
   public void setAvoidanceFinder(@Nullable Supplier<List<Avoidance>> avoidanceFinder) {
      this.avoidanceFinder = avoidanceFinder;
   }

   @Override
   public List<Avoidance> listAvoidedAreas() {
      if (!this.baritone().settings().avoidance.get()) {
         return Collections.emptyList();
      } else if (this.avoidanceFinder != null) {
         return this.avoidanceFinder.get();
      } else {
         List<Avoidance> res = new ArrayList<>();
         double mobCoeff = this.baritone().settings().mobAvoidanceCoefficient.get();
         if (mobCoeff != 1.0) {
            this.streamHostileEntities()
               .forEach(entity -> res.add(new Avoidance(entity.blockPosition(), mobCoeff, this.baritone().settings().mobAvoidanceRadius.get())));
         }

         return res;
      }
   }
}
