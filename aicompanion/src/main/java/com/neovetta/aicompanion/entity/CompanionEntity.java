package com.neovetta.aicompanion.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Player2APIService;
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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        if (!this.getWorld().isClient && shouldTickAi()) {
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
        if (!this.getWorld().isClient) {
            // Every tick, not age-gated: the regen timer counts ticks. Outside the brain gate too,
            // so a companion with no controller attached still heals.
            this.hungerManager.regenerateOnly(this);
            maybeSendRadar();
            maybeSendTokens();
            maybeWarnLowHealth();
        }
    }

    /**
     * Push a radar snapshot to the owner every 10 ticks: position, dimension, and health. Only when a
     * brain is attached and the owner is online — no owner, no packet. The client keeps the last
     * snapshot and decides when to draw it (distance/staleness/dimension), so there is nothing to send
     * or store when the companion is idle in an unloaded chunk: it simply stops ticking and the client
     * ages the snapshot out. See {@link AiCompanion#RADAR_UPDATE}.
     */
    private void maybeSendRadar() {
        if (this.controller == null || this.ownerUuid == null || this.age % 10 != 0) {
            return;
        }
        MinecraftServer server = this.getWorld().getServer();
        if (server == null) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(this.ownerUuid);
        if (owner == null) {
            return; // owner offline — nothing to draw a radar for
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(this.getX());
        buf.writeDouble(this.getY());
        buf.writeDouble(this.getZ());
        buf.writeIdentifier(this.getWorld().getRegistryKey().getValue());
        buf.writeFloat(this.getHealth());
        buf.writeFloat(this.getMaxHealth());
        ServerPlayNetworking.send(owner, AiCompanion.RADAR_UPDATE, buf);
    }

    /**
     * Push this session's cumulative LLM token spend to the owner every 20 ticks (~1s), on the same
     * "brain attached and owner online" gate as {@link #maybeSendRadar()} — that gate is what makes the
     * HUD appear only while a companion is actually spawned and thinking, with no separate liveness
     * signal to maintain.
     *
     * <p>The counters in {@code Player2APIService} are static and session-wide, so with two companions
     * out both push identical numbers and the client just takes the last one. Offset from the radar's
     * tick so the two packets don't land together. See {@link AiCompanion#TOKEN_USAGE}.
     */
    private void maybeSendTokens() {
        if (this.controller == null || this.ownerUuid == null || this.age % 20 != 5) {
            return;
        }
        MinecraftServer server = this.getWorld().getServer();
        if (server == null) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(this.ownerUuid);
        if (owner == null) {
            return; // owner offline — nobody to show a HUD to
        }
        Player2APIService.UsageSnapshot usage = Player2APIService.usageSnapshot();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(usage.promptTokens());
        buf.writeLong(usage.completionTokens());
        buf.writeLong(usage.totalTokens());
        buf.writeVarInt(usage.requests());
        ServerPlayNetworking.send(owner, AiCompanion.TOKEN_USAGE, buf);
    }

    /** Fraction of max health below which the owner is told, and above which the warning re-arms. */
    private static final float LOW_HEALTH_WARN = 0.30f;
    private static final float LOW_HEALTH_REARM = 0.60f;
    /** True once warned, cleared on recovery, so a hurt companion says it once rather than every tick. */
    private boolean lowHealthWarned = false;

    /**
     * Tell the owner once when the companion is badly hurt.
     *
     * <p>It has no self-preservation: in one session it dropped to 3/20 and carried on building for
     * the rest of the session without reacting or saying anything, one skeleton arrow away from
     * dying and scattering a full inventory. This does not fix that — it does not eat, heal or flee —
     * it just makes the state visible so the owner can decide.
     *
     * <p>Hysteresis between the two thresholds keeps a companion hovering near the line from
     * spamming; the notice re-arms only after a real recovery. Also routed to the agent's own log so
     * it can mention being hurt in conversation.
     */
    private void maybeWarnLowHealth() {
        if (this.controller == null || this.ownerUuid == null || this.age % 20 != 10) {
            return;
        }
        float max = this.getMaxHealth();
        if (max <= 0f) {
            return;
        }
        float fraction = this.getHealth() / max;
        if (fraction >= LOW_HEALTH_REARM) {
            lowHealthWarned = false;
            return;
        }
        if (fraction > LOW_HEALTH_WARN || lowHealthWarned) {
            return;
        }
        MinecraftServer server = this.getWorld().getServer();
        if (server == null) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(this.ownerUuid);
        if (owner == null) {
            return; // owner offline — warn when they next see it drop, not into the void
        }
        lowHealthWarned = true;
        String name = this.getCustomName() != null ? this.getCustomName().getString() : CompanionConfig.name();
        String note = String.format("%s is badly hurt — %.0f/%.0f health.", name, this.getHealth(), max);
        owner.sendMessage(Text.literal(note).formatted(Formatting.RED), false);
        AiCompanion.LOGGER.info("[{}] {}", AiCompanion.MOD_ID, note);
        if (this.controller != null) {
            this.controller.logAgentNotice(note + " Tell your owner you are hurt and need help.");
        }
    }

    /**
     * Whether the AI should run this tick: the world is up and someone is in it.
     *
     * <p>Not a safety mechanism — {@link CompanionTickGuard} is what makes companion world reads
     * incapable of blocking. This is only about not doing pointless work: during login and during
     * "Save and Quit" there is no one to act for, and the task engine has nothing useful to contribute.
     */
    private boolean shouldTickAi() {
        MinecraftServer server = this.getWorld().getServer();
        if (server == null || !server.isRunning() || server.isStopping() || server.isStopped()) {
            return false;
        }
        return this.getWorld() instanceof ServerWorld serverWorld && !serverWorld.getPlayers().isEmpty();
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
