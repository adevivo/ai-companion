package com.neovetta.aicompanion.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Character;
import adris.altoclef.util.CompanionTickGuard;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.entity.IAutomatone;
import baritone.api.entity.IHungerManagerProvider;
import baritone.api.entity.IInteractionManagerProvider;
import baritone.api.entity.IInventoryProvider;
import baritone.api.entity.LivingEntityHungerManager;
import baritone.api.entity.LivingEntityInteractionManager;
import baritone.api.entity.LivingEntityInventory;
import baritone.utils.accessor.ServerChunkManagerAccessor;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Our AI companion entity.
 *
 * <p>This is our own implementation of the PlayerEngine "player-abilities on a LivingEntity" pattern:
 * a {@link LivingEntity} that also carries a player-like inventory, interaction manager, and hunger
 * manager so the Automatone/AltoClef engine can drive it. Modelled on the upstream concept but written
 * for our mod (Player2NPC is unlicensed and is not copied).
 *
 * <p>Phase 1: the entity exists and carries its managers. The {@link AltoClefController} (which owns
 * navigation + tasks) is wired in the navigation step; until then {@code controller} stays null and is
 * guarded in {@link #tick()} so the entity is a harmless, LLM-free body.
 */
public class CompanionEntity extends LivingEntity
        implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {

    public LivingEntityInteractionManager interactionManager;
    public LivingEntityInventory inventory;
    public LivingEntityHungerManager hungerManager;

    /** Owns Baritone navigation + the AltoClef task engine. Server-side only; null until the nav step. */
    public AltoClefController controller;

    /**
     * Owner's UUID — the only part of the brain that survives a save. The {@link AltoClefController} is
     * runtime-only, so a companion restored from disk comes back as an AI-less body; this is what lets
     * {@link #maintainBrain()} rebuild it and re-find who it belongs to.
     */
    private UUID ownerUuid;

    /** Ticks until the next {@link #maintainBrain()} check — no need to re-resolve the owner 20x/sec. */
    private int brainCheckCooldown = 0;

    /**
     * Chunk radius around the companion that must be loaded before the AI may tick. Baritone raytraces
     * out to the companion's reach (a few blocks) each tick, so the immediate 3×3 neighbourhood is
     * ample — the entity could be standing at a chunk edge and still only reach one chunk over.
     */
    private static final int AI_TICK_GUARD_CHUNKS = 1;

    /** Consecutive ticks the AI has been held back by {@link #areSurroundingsLoaded()}. */
    private int ticksWaitingForChunks = 0;

    public CompanionEntity(EntityType<? extends CompanionEntity> type, World world) {
        super(type, world);
        this.setStepHeight(0.6f);
        setMovementSpeed(0.4f);
        this.interactionManager = new LivingEntityInteractionManager(this);
        this.inventory = new LivingEntityInventory(this);
        this.hungerManager = new LivingEntityHungerManager();
    }

    // --- PlayerEngine capability providers ---
    @Override
    public LivingEntityInventory getLivingInventory() {
        return inventory;
    }

    @Override
    public LivingEntityInteractionManager getInteractionManager() {
        return interactionManager;
    }

    @Override
    public LivingEntityHungerManager getHungerManager() {
        return hungerManager;
    }

    // --- Persistence: keep the player-like inventory across save/load ---
    @Override
    public void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        if (tag.contains("head_yaw")) {
            this.headYaw = tag.getFloat("head_yaw");
        }
        this.inventory.readNbt(tag.getList("Inventory", 10));
        this.inventory.selectedSlot = tag.getInt("SelectedItemSlot");
        if (tag.containsUuid("Owner")) {
            this.ownerUuid = tag.getUuid("Owner");
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        tag.putFloat("head_yaw", this.headYaw);
        tag.put("Inventory", this.inventory.writeNbt(new NbtList()));
        tag.putInt("SelectedItemSlot", this.inventory.selectedSlot);
        if (this.ownerUuid != null) {
            tag.putUuid("Owner", this.ownerUuid);
        }
    }

    // --- Ticking: drive the managers; the controller is guarded until the nav step ---
    @Override
    public void tick() {
        this.interactionManager.update();
        this.inventory.updateItems();
        lastAttackedTicks++; // LivingEntities don't tick attack cooldown by default
        if (!this.getWorld().isClient && isServerRunning() && areSurroundingsLoaded()) {
            // Inside this window the chunk source answers reads from memory instead of blocking on a
            // load — see CompanionTickGuard. Scoped to the AI only: super.tick() below must keep
            // vanilla's normal world access for physics and collision.
            CompanionTickGuard.begin();
            try {
                maintainBrain();
                if (this.controller != null) {
                    // Full agent: AltoClef tasks + Automatone nav + (on chat) the llama.cpp brain.
                    this.controller.serverTick();
                } else {
                    // Before a brain is attached, still drive Baritone so /companion goto works.
                    IBaritone.KEY.get(this).serverTick();
                }
            } finally {
                CompanionTickGuard.end();
            }
        }
        super.tick();
        this.tickHandSwing();
    }

    /**
     * Whether the server is in normal running state — false once a quit has been requested.
     *
     * <p>"Save and Quit" sets the server's running flag false and begins unloading chunks while the
     * tick loop is still draining. An AI tick landing in that window calls {@code getBlockState} on a
     * chunk that is on its way out, and the chunk cache blocks the server thread waiting for a load
     * that will never be scheduled — the game hangs on the "Saving world" screen. The AI has nothing
     * useful to contribute during shutdown, so the cheapest correct answer is not to run it.
     */
    private boolean isServerRunning() {
        MinecraftServer server = this.getWorld().getServer();
        return server != null && server.isRunning() && !server.isStopping() && !server.isStopped();
    }

    /**
     * Whether it is safe to tick the AI this tick — i.e. the chunks Baritone may touch are loaded.
     *
     * <p><b>Why this exists.</b> Every AI tick Baritone raytraces from the companion to find what it is
     * looking at ({@code BlockBreakHelper} → {@code EntityContext.objectMouseOver} →
     * {@code RayTraceUtils}). That raytrace calls {@code getBlockState}, and on an <em>unloaded</em>
     * chunk the server chunk cache answers by blocking the calling thread on a {@code CompletableFuture}
     * that only the server thread can complete — so the server thread deadlocks against itself.
     *
     * <p>It bites on world load: a companion restored from the save starts ticking while the chunks
     * around it are still loading, and the world never finishes loading (the player never joins, the
     * progress bar stops short, and there is no exception to explain it — just silence).
     *
     * <p>Two independent gates, because one was not enough:
     * <ol>
     *   <li><b>A player must be in the world.</b> The deadlock happens during login, before the joining
     *       player exists, so there is nothing for the companion to usefully do yet anyway.</li>
     *   <li><b>The 3×3 chunk neighbourhood must be <em>present</em></b>, tested with Baritone's own
     *       {@code automatone$getChunkNow} — a nullable, genuinely non-blocking lookup.
     *       {@code World.isChunkLoaded} is <em>not</em> an adequate substitute: it returned {@code true}
     *       for chunks that {@code getBlockState} then went and blocked on, which is how the first
     *       attempt at this guard failed.</li>
     * </ol>
     */
    private boolean areSurroundingsLoaded() {
        BlockPos pos = this.getBlockPos();
        if (!(this.getWorld() instanceof ServerWorld serverWorld) || serverWorld.getPlayers().isEmpty()) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        ServerChunkManagerAccessor chunks = (ServerChunkManagerAccessor) serverWorld.getChunkManager();
        boolean loaded = true;
        for (int dx = -AI_TICK_GUARD_CHUNKS; dx <= AI_TICK_GUARD_CHUNKS && loaded; dx++) {
            for (int dz = -AI_TICK_GUARD_CHUNKS; dz <= AI_TICK_GUARD_CHUNKS && loaded; dz++) {
                loaded = chunks.automatone$getChunkNow(chunkX + dx, chunkZ + dz) != null;
            }
        }
        if (loaded) {
            ticksWaitingForChunks = 0;
        } else if (++ticksWaitingForChunks == 200) {
            // Ten seconds of waiting is no longer "the world is still loading". Say so once, so a
            // permanently idle companion is a diagnosable condition rather than a mystery.
            AiCompanion.LOGGER.warn("[{}] companion at {} has been waiting {} ticks for its surrounding "
                    + "chunks to load; AI is paused until they do",
                    AiCompanion.MOD_ID, pos.toShortString(), ticksWaitingForChunks);
        }
        return loaded;
    }

    /** Attach the agent brain (AltoClef controller) to this companion, owned by {@code owner}. */
    public void initBrain(Character character, PlayerEntity owner) {
        this.controller = new AltoClefController(IBaritone.KEY.get(this), character, "aicompanion");
        this.controller.setOwner(owner);
        this.ownerUuid = owner.getUuid();
    }

    /**
     * Keep the brain attached and its owner reference current.
     *
     * <p>Two cases, both of which otherwise leave a body with no AI:
     * <ul>
     *   <li><b>Restored from a save.</b> Only {@link #ownerUuid} persists — the controller does not — so
     *       rebuild it from config once the owner is online. Identity comes from
     *       {@link CompanionConfig#character()}, so config remains the source of truth.</li>
     *   <li><b>Owner relogged.</b> A reconnecting player is a <em>new</em> entity object, so the cached
     *       reference goes stale and would aim chat and TTS packets at a disconnected player.</li>
     * </ul>
     *
     * <p>Deliberately does nothing while the owner is offline: ownership never silently transfers.
     * Re-attaching costs no LLM call (the controller only registers conversation state; nothing greets).
     */
    private void maintainBrain() {
        if (this.ownerUuid == null || --this.brainCheckCooldown > 0) {
            return;
        }
        this.brainCheckCooldown = 20;

        PlayerEntity owner = this.getWorld().getPlayerByUuid(this.ownerUuid);
        if (owner == null) {
            return; // wait for the real owner rather than adopting whoever is nearby
        }
        if (this.controller == null) {
            initBrain(CompanionConfig.character(), owner);
            AiCompanion.LOGGER.info("[{}] re-attached brain to restored companion (owner {})",
                    AiCompanion.MOD_ID, owner.getName().getString());
        } else if (this.controller.getOwner() != owner) {
            this.controller.setOwner(owner);
        }
    }

    public AltoClefController getController() {
        return this.controller;
    }

    /** Path to a block position using Baritone (server-side). Phase-1 navigation entrypoint. */
    public void goTo(BlockPos pos) {
        IBaritone.KEY.get(this).getCustomGoalProcess()
                .setGoalAndPath(new GoalBlock(pos.getX(), pos.getY(), pos.getZ()));
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        this.headYaw = this.getYaw();
        pickupItems();
    }

    /** Pick up nearby items so gathered materials land in the player-like inventory. */
    private void pickupItems() {
        if (this.getWorld().isClient || !this.isAlive() || this.dead
                || !this.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return;
        }
        Vec3i r = new Vec3i(2, 1, 2);
        for (ItemEntity item : this.getWorld().getNonSpectatingEntities(ItemEntity.class,
                this.getBoundingBox().expand(r.getX(), r.getY(), r.getZ()))) {
            if (item.isRemoved() || item.getStack().isEmpty() || item.cannotPickup()) {
                continue;
            }
            ItemStack stack = item.getStack();
            int count = stack.getCount();
            if (this.getLivingInventory().insertStack(stack)) {
                this.sendPickup(item, count);
                if (stack.isEmpty()) {
                    item.discard();
                    stack.setCount(count);
                }
            }
        }
    }

    // --- Combat: LivingEntity has no attack of its own ---
    @Override
    public boolean tryAttack(Entity target) {
        lastAttackedTicks = 0;
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float knockback = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_KNOCKBACK);
        if (target instanceof LivingEntity living) {
            damage += EnchantmentHelper.getAttackDamage(this.getMainHandStack(), living.getGroup());
            knockback += EnchantmentHelper.getKnockback(this);
        }
        int fire = EnchantmentHelper.getFireAspect(this);
        if (fire > 0) {
            target.setOnFireFor(fire * 4);
        }
        boolean hit = target.damage(this.getDamageSources().mobAttack(this), damage);
        if (hit) {
            if (knockback > 0.0F && target instanceof LivingEntity living) {
                living.takeKnockback(knockback * 0.5F,
                        MathHelper.sin(this.getYaw() * ((float) Math.PI / 180F)),
                        -MathHelper.cos(this.getYaw() * ((float) Math.PI / 180F)));
                this.setVelocity(this.getVelocity().multiply(0.6, 1.0, 0.6));
            }
            this.applyDamageEffects(this, target);
            this.onAttacking(target);
        }
        return hit;
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
        if (this.velocityModified) {
            super.takeKnockback(strength, x, z);
        }
    }

    // --- Equipment plumbing backed by the player-like inventory ---
    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return getLivingInventory().armor;
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            return this.inventory.getMainHandStack();
        } else if (slot == EquipmentSlot.OFFHAND) {
            return this.inventory.offHand.get(0);
        }
        return slot.getType() == EquipmentSlot.Type.ARMOR
                ? this.inventory.armor.get(slot.getEntitySlotId())
                : ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            this.inventory.setStack(this.inventory.selectedSlot, stack);
        } else if (slot == EquipmentSlot.OFFHAND) {
            this.inventory.offHand.set(0, stack);
        } else if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            this.inventory.armor.set(slot.getEntitySlotId(), stack);
        }
    }
}
