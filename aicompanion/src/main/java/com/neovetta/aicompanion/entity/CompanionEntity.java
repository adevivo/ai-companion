package com.neovetta.aicompanion.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.manager.ConversationManager;
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
import net.minecraft.entity.damage.DamageSource;
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

import java.util.List;
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

    /** Consecutive AI updates that ended in an exception. Reset by any tick that completes. */
    private int aiFailures;
    /** Set once the AI has failed too many times in a row; cleared by `/companion reload`. */
    private boolean aiDisabled;
    /**
     * How many consecutive failures before the AI is switched off.
     *
     * <p>More than one because a transient fault — a chunk that was not loaded, a race with a world
     * save — should heal itself without the owner doing anything. Not many more, because a genuinely
     * broken state would otherwise throw twenty times a second forever.
     */
    private static final int MAX_AI_FAILURES = 5;

    /**
     * Absorb an exception from the AI so it cannot take the server down with it.
     *
     * <p>The AI runs inside this entity's tick, and Minecraft treats anything thrown out of an entity
     * tick as unrecoverable: it raises "Ticking entity" and shuts the server down, which for a
     * singleplayer world means the session ends. That is what a single null player reference in
     * block-placement did — see {@code EntityPlaceContext}. A defect in the companion should cost the
     * companion, not the world.
     */
    private void onAiTickFailed(Throwable failure) {
        aiFailures++;
        // The counters are raised before anything that could itself fail. This method is the last
        // thing standing between a broken AI and a dead server, so reporting the problem must never
        // become a second way to crash: reading the player list or the custom name touches state that
        // may be exactly what went wrong.
        if (aiFailures >= MAX_AI_FAILURES) {
            aiDisabled = true;
        }
        try {
            String name = this.getCustomName() != null
                    ? this.getCustomName().getString()
                    : CompanionConfig.name();
            // Trace only on the first failure of a run — at 20 ticks a second, logging every one
            // would bury the original cause under thousands of copies of itself.
            if (aiFailures == 1) {
                AiCompanion.LOGGER.error("[{}] {}'s AI threw during tick; skipping this update",
                        AiCompanion.MOD_ID, name, failure);
                tellOwner(name + " hit an internal error and skipped a step. Watch for it repeating.");
            }
            if (aiDisabled) {
                AiCompanion.LOGGER.error("[{}] {}'s AI failed {} times in a row; disabling it. Last error:",
                        AiCompanion.MOD_ID, name, aiFailures, failure);
                tellOwner(name + "'s brain has stopped working — use /companion reload to restart it.");
            }
        } catch (Throwable reportingFailure) {
            // Nothing left to do but note it and keep the world alive.
            AiCompanion.LOGGER.error("[{}] Could not report a companion AI failure",
                    AiCompanion.MOD_ID, reportingFailure);
        }
    }

    /** Puts a line in the owner's chat, if they are online to read it. */
    private void tellOwner(String message) {
        MinecraftServer server = this.getWorld().getServer();
        if (server == null || this.ownerUuid == null) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(this.ownerUuid);
        if (owner != null) {
            owner.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
        }
    }

    /** Lets the AI run again after it was switched off. Called by {@code /companion reload}. */
    public void resetAiFailures() {
        aiFailures = 0;
        aiDisabled = false;
    }

    // --- Ticking: drive the managers; the controller is guarded until the nav step ---
    @Override
    public void tick() {
        this.interactionManager.update();
        this.inventory.updateItems();
        lastAttackedTicks++; // LivingEntities don't tick attack cooldown by default
        if (!this.getWorld().isClient && !aiDisabled && shouldTickAi()) {
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
                aiFailures = 0; // a clean tick clears whatever went wrong before it
            } catch (Throwable failure) {
                onAiTickFailed(failure);
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
        if (server == null || this.ownerUuid == null
                || server.getPlayerManager().getPlayer(this.ownerUuid) == null) {
            return; // owner offline — warn when they next see it drop, not into the void
        }
        lowHealthWarned = true;
        String name = this.getCustomName() != null ? this.getCustomName().getString() : CompanionConfig.name();
        String note = String.format("%s is badly hurt — %.0f/%.0f health.", name, this.getHealth(), max);
        tellOwner(note);
        AiCompanion.LOGGER.info("[{}] {}", AiCompanion.MOD_ID, note);
        if (this.controller != null) {
            // Info, not a notice: this fires from the entity tick, unrelated to whatever command is
            // running, and logAgentNotice would leave a pending failure for that command to report.
            this.controller.logAgentInfo(note + " Tell your owner you are hurt and need help.");
        }
    }

    // --- Death: die like a player, drop like a player ---

    /**
     * Scatter everything the companion was carrying at the place it died.
     *
     * <p>Without this the inventory is simply garbage-collected with the entity: {@link LivingEntity}
     * has no drop-on-death behaviour of its own — that lives in {@code PlayerEntity}, and only for a
     * player's own inventory — so a companion carrying a build's worth of materials took all of it with
     * it. {@link #dropInventory()} is vanilla's hook for exactly this and is called unconditionally from
     * {@code LivingEntity.drop(DamageSource)}, on the server, once per death.
     *
     * <p><b>{@code keepInventory} is deliberately ignored.</b> For a player the rule moves items to the
     * respawned body; a companion has no respawn — it is removed from the world 20 ticks after dying and
     * never comes back — so honouring the rule would keep the stacks on an entity that ceases to exist,
     * which is the bug this fixes, just conditional on a gamerule. Always dropping means nothing is ever
     * lost. {@code doMobLoot} is not consulted either: these are items the owner handed over or the
     * companion gathered, not mob loot.
     *
     * <p>Armour is included here rather than left to {@code dropEquipment}: that hook is empty on
     * {@link LivingEntity} (only {@code MobEntity} implements it), so there is nothing to double up with.
     */
    @Override
    protected void dropInventory() {
        super.dropInventory();
        if (this.getWorld().isClient) {
            return;
        }
        int stacks = dropAll(this.inventory.main)
                + dropAll(this.inventory.armor)
                + dropAll(this.inventory.offHand);
        announceDeath(stacks);
    }

    /**
     * Drop every non-empty stack in one of the inventory's backing lists and return how many were
     * dropped. The slot is cleared <em>before</em> the drop: {@code dropStack} hands the very same
     * {@link ItemStack} instance to the new {@link ItemEntity} rather than copying it, so leaving it in
     * place would alias a stack that now belongs to the world.
     */
    private int dropAll(List<ItemStack> slots) {
        int dropped = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            slots.set(i, ItemStack.EMPTY);
            this.dropStack(stack, 0.5f); // waist height, so nothing spawns inside the floor
            dropped++;
        }
        return dropped;
    }

    /**
     * Tell the owner where it died and that there is something to go and collect — the radar stops
     * updating the moment the entity is removed, and dropped items despawn after five minutes.
     *
     * <p>Everything here is best-effort and wrapped: this runs inside the entity tick, where anything
     * thrown is a "Ticking entity" crash that ends the session, and reading the custom name or the
     * player list touches exactly the state that a broken companion is likely to have corrupted. The
     * drop itself has already happened by this point, so a failure here costs a chat line, not items.
     */
    private void announceDeath(int stacks) {
        try {
            String name = this.getCustomName() != null
                    ? this.getCustomName().getString()
                    : CompanionConfig.name();
            BlockPos pos = this.getBlockPos();
            String where = String.format("%s died at %d, %d, %d", name, pos.getX(), pos.getY(), pos.getZ());
            String note = stacks == 0
                    ? where + " — it was carrying nothing."
                    : String.format("%s and dropped %d stack%s. Items despawn in 5 minutes.",
                            where, stacks, stacks == 1 ? "" : "s");
            AiCompanion.LOGGER.info("[{}] {}", AiCompanion.MOD_ID, note);
            tellOwner(note);
        } catch (Throwable reportingFailure) {
            AiCompanion.LOGGER.error("[{}] Could not report a companion death", AiCompanion.MOD_ID,
                    reportingFailure);
        }
    }

    /**
     * Release the conversation state along with the body.
     *
     * <p>{@code ConversationManager} keys on the entity UUID and never cleans up on its own; a dead
     * companion's UUID is never seen again (a replacement is a new entity), so without this every death
     * leaks a full conversation history for the rest of the session. {@code /companion despawn} already
     * does the same thing for the same reason.
     */
    @Override
    public void onDeath(DamageSource source) {
        boolean wasDying = this.dead; // onDeath is guarded but not documented as once-only
        super.onDeath(source);
        if (!this.getWorld().isClient && !wasDying) {
            ConversationManager.forget(this.getUuid());
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

    /**
     * Pick up nearby items so gathered materials land in the player-like inventory.
     *
     * <p>Unfiltered below the last free slot — every cobblestone knocked loose while pathing, every
     * leaf drop. That is how she arrives at a full inventory carrying 192 dirt and 64 torch nothing
     * asked for, and a full inventory is not merely untidy: it stops collection dead, and the gather
     * that needed one more log then retries forever (measured 2026-07-29 — 3,814 failed pickups over
     * 27 minutes, nothing built).
     *
     * <p>So once there is no free slot, only take what merges into a stack already held. Gathering is
     * unaffected while there is room, which is the overwhelmingly common case; at the boundary she
     * stops spending her last slots on debris and keeps topping up what she is actually collecting.
     * Filtering by what the active task wants would be the better answer, but gathering runs through
     * this same path and that is a much larger change.
     */
    private void pickupItems() {
        if (this.getWorld().isClient || !this.isAlive() || this.dead
                || !this.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return;
        }
        boolean full = this.getLivingInventory().getEmptySlot() < 0;
        Vec3i r = new Vec3i(2, 1, 2);
        for (ItemEntity item : this.getWorld().getNonSpectatingEntities(ItemEntity.class,
                this.getBoundingBox().expand(r.getX(), r.getY(), r.getZ()))) {
            if (item.isRemoved() || item.getStack().isEmpty() || item.cannotPickup()) {
                continue;
            }
            ItemStack stack = item.getStack();
            if (full && !canMergeIntoHeldStack(stack)) {
                continue;
            }
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

    /** Whether {@code stack} can join a partial stack already carried, i.e. needs no free slot. */
    private boolean canMergeIntoHeldStack(ItemStack stack) {
        LivingEntityInventory inventory = this.getLivingInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack held = inventory.main.get(i);
            if (!held.isEmpty() && held.getCount() < held.getMaxCount()
                    && ItemStack.canCombine(held, stack)) {
                return true;
            }
        }
        return false;
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
