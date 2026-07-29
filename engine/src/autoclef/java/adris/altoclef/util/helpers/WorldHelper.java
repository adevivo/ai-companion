package adris.altoclef.util.helpers;

import adris.altoclef.AltoClefController;
import adris.altoclef.mixins.EntityAccessor;
import adris.altoclef.multiversion.MethodWrapper;
import adris.altoclef.multiversion.world.WorldVer;
import adris.altoclef.util.Dimension;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.MovementHelper;
import baritone.process.MineProcess;
import baritone.utils.BlockStateInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public interface WorldHelper {
   static Vec3 toVec3d(BlockPos pos) {
      return pos == null ? null : new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
   }

   static Vec3 toVec3d(Vec3i pos) {
      return new Vec3(pos.getX(), pos.getY(), pos.getZ());
   }

   static Vec3i toVec3i(Vec3 pos) {
      return new Vec3i((int)pos.x(), (int)pos.y(), (int)pos.z());
   }

   static BlockPos toBlockPos(Vec3 pos) {
      return new BlockPos((int)pos.x(), (int)pos.y(), (int)pos.z());
   }

   static boolean isSourceBlock(AltoClefController controller, BlockPos pos, boolean onlyAcceptStill) {
      Level world = controller.getWorld();
      BlockState s = world.getBlockState(pos);
      if (s.getBlock() instanceof LiquidBlock) {
         if (!s.getFluidState().isSource() && onlyAcceptStill) {
            return false;
         } else {
            int level = s.getFluidState().getAmount();
            BlockState above = world.getBlockState(pos.above());
            return above.getBlock() instanceof LiquidBlock ? false : level == 8;
         }
      } else {
         return false;
      }
   }

   static double distanceXZSquared(Vec3 from, Vec3 to) {
      Vec3 delta = to.subtract(from);
      return delta.x * delta.x + delta.z * delta.z;
   }

   static double distanceXZ(Vec3 from, Vec3 to) {
      return Math.sqrt(distanceXZSquared(from, to));
   }

   static boolean inRangeXZ(Vec3 from, Vec3 to, double range) {
      return distanceXZSquared(from, to) < range * range;
   }

   static boolean inRangeXZ(BlockPos from, BlockPos to, double range) {
      return inRangeXZ(toVec3d(from), toVec3d(to), range);
   }

   static boolean inRangeXZ(Entity entity, Vec3 to, double range) {
      return inRangeXZ(entity.position(), to, range);
   }

   static boolean inRangeXZ(Entity entity, BlockPos to, double range) {
      return inRangeXZ(entity, toVec3d(to), range);
   }

   static boolean inRangeXZ(Entity entity, Entity to, double range) {
      return inRangeXZ(entity, to.position(), range);
   }

   static Dimension getCurrentDimension(AltoClefController controller) {
      Level world = controller.getWorld();
      if (world == null) {
         return Dimension.OVERWORLD;
      } else if (world.dimensionType().ultraWarm()) {
         return Dimension.NETHER;
      } else {
         return world.dimensionType().natural() ? Dimension.OVERWORLD : Dimension.END;
      }
   }

   static boolean isSolidBlock(AltoClefController controller, BlockPos pos) {
      Level world = controller.getWorld();
      return world.getBlockState(pos).isRedstoneConductor(world, pos);
   }

   static BlockPos getBedHead(AltoClefController controller, BlockPos posWithBed) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(posWithBed);
      if (state.getBlock() instanceof BedBlock) {
         Direction facing = (Direction)state.getValue(BedBlock.FACING);
         return ((BedPart)world.getBlockState(posWithBed).getValue(BedBlock.PART)).equals(BedPart.HEAD) ? posWithBed : posWithBed.relative(facing);
      } else {
         return null;
      }
   }

   static BlockPos getBedFoot(AltoClefController controller, BlockPos posWithBed) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(posWithBed);
      if (state.getBlock() instanceof BedBlock) {
         Direction facing = (Direction)state.getValue(BedBlock.FACING);
         return ((BedPart)world.getBlockState(posWithBed).getValue(BedBlock.PART)).equals(BedPart.FOOT)
            ? posWithBed
            : posWithBed.relative(facing.getOpposite());
      } else {
         return null;
      }
   }

   /**
    * Y of the topmost solid ground block in a column — the block you would stand on top of.
    *
    * <p>Backed by the chunk heightmap, so this is O(1) per column. Prefer it to
    * {@link #getGroundHeight(AltoClefController, int, int)}, which walks the whole column down from
    * the build limit (~384 block lookups) and reports the topmost redstone conductor, meaning a tree
    * or an overhang above the site answers with the log height instead of the ground.
    *
    * <p>{@code MOTION_BLOCKING_NO_LEAVES} rather than {@code WORLD_SURFACE}: the latter counts any
    * non-air block, so grass and flowers read one block high. The {@code _WG} variants are worldgen
    * only ({@code Heightmap.Types#keepAfterWorldgen}) and are not maintained on a loaded chunk.
    *
    * <p>The {@code - 1} is not slack: {@code ChunkAccess#getHeight} returns {@code getFirstAvailable()
    * - 1} (the topmost matching block) and {@code Level#getHeight} adds one back, so the level-level
    * call yields the first FREE position above the ground — a standing entity's feet, not the ground.
    * Verified against 1.20.1 bytecode.
    *
    * @return the ground block's Y, or {@code level.getMinBuildHeight() - 1} if the chunk is unloaded
    */
   static int surfaceY(AltoClefController controller, int x, int z) {
      Level world = controller.getWorld();
      return world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
   }

   /**
    * How far below the sky surface a reference point has to be before it counts as underground.
    *
    * <p>Kept equal to {@code BuildPlacement.MAX_LIFT} on purpose: everything within that distance of
    * the surface is a case the ground check already handles correctly (at the surface, above it, or
    * buried shallowly enough to be lifted onto it), so those must keep the sky-heightmap answer
    * unchanged. Only offsets bigger than this reach the local-floor search — and those are refused
    * outright today, so the search can only ever turn a refusal into a build.
    */
   int UNDERGROUND_THRESHOLD = 3;

   /** How far {@link #groundYNear} will walk down looking for a floor before giving up. */
   int LOCAL_FLOOR_SEARCH_DEPTH = 64;

   /**
    * Ground Y at {@code (x, z)} as seen from {@code refY}: the sky surface when {@code refY} is at or
    * near it, and the floor of the enclosed space {@code refY} sits in when it is far below.
    *
    * <p>{@link #surfaceY} answers with the top of the column as seen from the sky, which is the wrong
    * question inside a cave: standing on a cave floor at y=41 under 28 blocks of stone it reports the
    * terrain overhead, so a build aimed correctly at the companion's own feet measured as 28 blocks
    * underground and was refused. Every build underground was refused at every Y, and the same number
    * reached the model as {@code groundLevel} while the prompt promised it was the block being stood on.
    *
    * <p>Deliberately gated rather than always searching. Above ground the sky heightmap is right and
    * is what the placement work in 0.1.9/0.2.0 was tuned against, so {@code UNDERGROUND_THRESHOLD}
    * short-circuits every ordinary column — including water, farmland and tree columns, where the
    * per-block solidity tests below disagree with the heightmap.
    *
    * <p>The floor test is "solid with open space above" rather than merely "solid" so that a Y
    * hallucinated deep inside rock finds nothing, falls back, and stays refused instead of quietly
    * burying the build. Solidity is {@link BlockState#blocksMotion()} to match what the heightmap
    * counts; {@link #isSolidBlock} is {@code isRedstoneConductor}, which is false for water and for
    * farmland (not a full collision box).
    *
    * @param refY the Y the caller is reasoning from — the entity's feet, or a build plan's base layer
    */
   static int groundYNear(AltoClefController controller, int x, int z, int refY) {
      int surface = surfaceY(controller, x, z);
      if (surface - refY <= UNDERGROUND_THRESHOLD) {
         return surface;
      }
      Level world = controller.getWorld();
      int floor = Math.max(world.getMinBuildHeight(), refY - LOCAL_FLOOR_SEARCH_DEPTH);
      for (int y = refY; y >= floor; y--) {
         if (world.getBlockState(new BlockPos(x, y, z)).blocksMotion()
               && !world.getBlockState(new BlockPos(x, y + 1, z)).blocksMotion()) {
            return y;
         }
      }
      return surface;
   }

   static int getGroundHeight(AltoClefController controller, int x, int z) {
      Level world = controller.getWorld();

      for (int y = world.getMaxBuildHeight(); y >= world.getMinBuildHeight(); y--) {
         BlockPos check = new BlockPos(x, y, z);
         if (isSolidBlock(controller, check)) {
            return y;
         }
      }

      return -1;
   }

   static BlockPos getADesertTemple(AltoClefController controller) {
      Level world = controller.getWorld();
      List<BlockPos> stonePressurePlates = controller.getBlockScanner().getKnownLocations(Blocks.STONE_PRESSURE_PLATE);
      if (!stonePressurePlates.isEmpty()) {
         for (BlockPos pos : stonePressurePlates) {
            if (world.getBlockState(pos).getBlock() == Blocks.STONE_PRESSURE_PLATE
               && world.getBlockState(pos.below()).getBlock() == Blocks.CUT_SANDSTONE
               && world.getBlockState(pos.below(2)).getBlock() == Blocks.TNT) {
               return pos;
            }
         }
      }

      return null;
   }

   static boolean isUnopenedChest(AltoClefController controller, BlockPos pos) {
      return controller.getItemStorage().getContainerAtPosition(pos).isEmpty();
   }

   static int getGroundHeight(AltoClefController controller, int x, int z, Block... groundBlocks) {
      Level world = controller.getWorld();
      Set<Block> possibleBlocks = new HashSet<>(Arrays.asList(groundBlocks));

      for (int y = world.getMaxBuildHeight(); y >= world.getMinBuildHeight(); y--) {
         BlockPos check = new BlockPos(x, y, z);
         if (possibleBlocks.contains(world.getBlockState(check).getBlock())) {
            return y;
         }
      }

      return -1;
   }

   static boolean canBreak(AltoClefController controller, BlockPos pos) {
      boolean prevInteractionPaused = controller.getExtraBaritoneSettings().isInteractionPaused();
      controller.getExtraBaritoneSettings().setInteractionPaused(false);
      boolean canBreak = controller.getWorld().getBlockState(pos).getDestroySpeed(controller.getWorld(), pos) >= 0.0F
         && !controller.getExtraBaritoneSettings().shouldAvoidBreaking(pos)
         && MineProcess.plausibleToBreak(new CalculationContext(controller.getBaritone()), pos)
         && canReach(controller, pos);
      controller.getExtraBaritoneSettings().setInteractionPaused(prevInteractionPaused);
      return canBreak;
   }

   static boolean isInNetherPortal(AltoClefController controller) {
      LivingEntity player = controller.getPlayer();
      return player == null ? false : ((EntityAccessor)player).isInNetherPortal();
   }

   static boolean canPlace(AltoClefController controller, BlockPos pos) {
      return !controller.getExtraBaritoneSettings().shouldAvoidPlacingAt(pos) && canReach(controller, pos);
   }

   static boolean canReach(AltoClefController controller, BlockPos pos) {
      return controller.getModSettings().shouldAvoidOcean()
            && controller.getPlayer().getY() > 47.0
            && controller.getChunkTracker().isChunkLoaded(pos)
            && isOcean(controller.getWorld().getBiome(pos))
            && pos.getY() < 64
            && getGroundHeight(controller, pos.getX(), pos.getZ(), Blocks.WATER) > pos.getY()
         ? false
         : !controller.getBlockScanner().isUnreachable(pos);
   }

   static boolean isOcean(Holder<Biome> b) {
      return WorldVer.isBiome(b, Biomes.OCEAN)
         || WorldVer.isBiome(b, Biomes.COLD_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_COLD_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_FROZEN_OCEAN)
         || WorldVer.isBiome(b, Biomes.DEEP_LUKEWARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.LUKEWARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.WARM_OCEAN)
         || WorldVer.isBiome(b, Biomes.FROZEN_OCEAN);
   }

   static boolean isAir(AltoClefController controller, BlockPos pos) {
      return controller.getBlockScanner().isBlockAtPosition(pos, Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR);
   }

   static boolean isAir(Block block) {
      return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
   }

   static boolean isInteractableBlock(AltoClefController controller, BlockPos pos) {
      Block block = controller.getWorld().getBlockState(pos).getBlock();
      return block instanceof ChestBlock
         || block instanceof EnderChestBlock
         || block instanceof CraftingTableBlock
         || block instanceof AbstractFurnaceBlock
         || block instanceof LoomBlock
         || block instanceof CartographyTableBlock
         || block instanceof EnchantmentTableBlock
         || block instanceof RedStoneOreBlock
         || block instanceof BarrelBlock;
   }

   static boolean isInsidePlayer(AltoClefController controller, BlockPos pos) {
      return pos.closerToCenterThan(controller.getPlayer().position(), 2.0);
   }

   static Iterable<BlockPos> getBlocksTouchingPlayer(LivingEntity player) {
      return getBlocksTouchingBox(player.getBoundingBox());
   }

   static Iterable<BlockPos> getBlocksTouchingBox(AABB box) {
      BlockPos min = new BlockPos((int)box.minX, (int)box.minY, (int)box.minZ);
      BlockPos max = new BlockPos((int)box.maxX, (int)box.maxY, (int)box.maxZ);
      return scanRegion(min, max);
   }

   static Iterable<BlockPos> scanRegion(BlockPos start, BlockPos end) {
      return () -> new Iterator<BlockPos>() {
         int x = start.getX();
         int y = start.getY();
         int z = start.getZ();

         @Override
         public boolean hasNext() {
            return this.y <= end.getY() && this.z <= end.getZ() && this.x <= end.getX();
         }

         public BlockPos next() {
            BlockPos result = new BlockPos(this.x, this.y, this.z);
            this.x++;
            if (this.x > end.getX()) {
               this.x = start.getX();
               this.z++;
               if (this.z > end.getZ()) {
                  this.z = start.getZ();
                  this.y++;
               }
            }

            return result;
         }
      };
   }

   static boolean fallingBlockSafeToBreak(AltoClefController controller, BlockPos pos) {
      BlockStateInterface bsi = new BlockStateInterface(controller.getBaritone().getEntityContext());
      Level clientWorld = controller.getWorld();
      if (clientWorld == null) {
         throw new AssertionError();
      } else {
         while (isFallingBlock(controller, pos)) {
            if (MovementHelper.avoidBreaking(bsi, pos.getX(), pos.getY(), pos.getZ(), clientWorld.getBlockState(pos), controller.getBaritoneSettings())) {
               return false;
            }

            pos = pos.above();
         }

         return true;
      }
   }

   static boolean isFallingBlock(AltoClefController controller, BlockPos pos) {
      Level clientWorld = controller.getWorld();
      if (clientWorld == null) {
         throw new AssertionError();
      } else {
         return clientWorld.getBlockState(pos).getBlock() instanceof FallingBlock;
      }
   }

   static Entity getSpawnerEntity(AltoClefController controller, BlockPos pos) {
      Level world = controller.getWorld();
      BlockState state = world.getBlockState(pos);
      return state.getBlock() instanceof SpawnerBlock && world.getBlockEntity(pos) instanceof SpawnerBlockEntity blockEntity
         ? MethodWrapper.getRenderedEntity(blockEntity.getSpawner(), world, pos)
         : null;
   }

   static boolean isChest(AltoClefController controller, BlockPos block) {
      Block b = controller.getWorld().getBlockState(block).getBlock();
      return isChest(b);
   }

   static boolean isChest(Block b) {
      return b instanceof ChestBlock || b instanceof EnderChestBlock;
   }

   static boolean isBlock(AltoClefController controller, BlockPos pos, Block block) {
      return controller.getWorld().getBlockState(pos).getBlock() == block;
   }

   static boolean canSleep(AltoClefController controller) {
      Level world = controller.getWorld();
      if (world != null) {
         if (world.isThundering() && world.isRaining()) {
            return true;
         } else {
            int time = getTimeOfDay(controller);
            return 12542 <= time && time <= 23992;
         }
      } else {
         return false;
      }
   }

   static int getTimeOfDay(AltoClefController controller) {
      Level world = controller.getWorld();
      return world != null ? (int)(world.getDayTime() % 24000L) : 0;
   }

   static boolean isVulnerable(LivingEntity player) {
      int armor = player.getArmorValue();
      float health = player.getHealth();
      if (armor <= 15 && health < 3.0F) {
         return true;
      } else {
         return armor < 10 && health < 10.0F ? true : armor < 5 && health < 18.0F;
      }
   }

   static boolean isSurroundedByHostiles(AltoClefController controller) {
      List<LivingEntity> hostiles = controller.getEntityTracker().getHostiles();
      return isSurrounded(controller, hostiles);
   }

   static boolean isSurrounded(AltoClefController controller, List<LivingEntity> entities) {
      LivingEntity player = controller.getPlayer();
      BlockPos playerPos = player.blockPosition();
      int MIN_SIDES_TO_SURROUND = 2;
      List<Direction> uniqueSides = new ArrayList<>();

      for (Entity entity : entities) {
         if (entity.closerThan(player, 8.0)) {
            BlockPos entityPos = entity.blockPosition();
            double angle = calculateAngle(playerPos, entityPos);
            boolean isUnique = !uniqueSides.contains(getHorizontalDirectionFromYaw(angle));
            if (isUnique) {
               uniqueSides.add(getHorizontalDirectionFromYaw(angle));
            }
         }
      }

      return uniqueSides.size() >= 2;
   }

   private static double calculateAngle(BlockPos origin, BlockPos target) {
      double translatedX = target.getX() - origin.getX();
      double translatedZ = target.getZ() - origin.getZ();
      double angleRad = Math.atan2(translatedZ, translatedX);
      double angleDeg = Math.toDegrees(angleRad);
      angleDeg -= 90.0;
      if (angleDeg < 0.0) {
         angleDeg += 360.0;
      }

      return angleDeg;
   }

   private static Direction getHorizontalDirectionFromYaw(double yaw) {
      yaw %= 360.0;
      if (yaw < 0.0) {
         yaw += 360.0;
      }

      if ((!(yaw >= 45.0) || !(yaw < 135.0)) && (!(yaw >= -315.0) || !(yaw < -225.0))) {
         if ((!(yaw >= 135.0) || !(yaw < 225.0)) && (!(yaw >= -225.0) || !(yaw < -135.0))) {
            return (!(yaw >= 225.0) || !(yaw < 315.0)) && (!(yaw >= -135.0) || !(yaw < -45.0)) ? Direction.SOUTH : Direction.EAST;
         } else {
            return Direction.NORTH;
         }
      } else {
         return Direction.WEST;
      }
   }
}
