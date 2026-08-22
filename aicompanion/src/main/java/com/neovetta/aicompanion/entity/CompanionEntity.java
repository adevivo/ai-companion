package com.neovetta.aicompanion.entity;

import adris.altoclef.AltoClefController;
import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.Player2APIService;
import adris.altoclef.player2api.manager.ConversationManager;
import adris.altoclef.util.CompanionTickGuard;
import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CombatConfig;
import com.neovetta.aicompanion.CompanionConfig;
import com.neovetta.aicompanion.SkinProfileResolver;
import com.neovetta.aicompanion.screen.CompanionScreenHandlerFactory;
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
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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

    /**
     * Which {@link CompanionConfig.RosterEntry} this companion is, by name. Persisted, because it is
     * what a companion restored from a save uses to come back as itself — without it every reload
     * would rebuild every companion from the default entry and a roster would collapse into clones.
     */
    private String rosterName = "";

    /**
     * Who this companion actually is, persisted whole.
     *
     * <p>⚠️ This is what makes a client-owned roster survive its owner. Identity used to be a name
     * plus a lookup in the server's config, which works only while the server is the place identity
     * lives. Now a player brings their own, so the server has no entry to look up once they log off
     * — and if the operator happens to have a companion with the same name, the lookup finds the
     * wrong one and swaps the two silently.
     *
     * <p>Null for a companion saved before this existed; {@code entryFor} falls back to the old
     * name lookup in that case, which is what those companions have always done.
     */
    private CompanionConfig.RosterEntry identity;

    /**
     * Whether this companion and its owner have met.
     *
     * <p>Drives "welcome back" versus an introduction. It used to be inferred from whether a
     * conversation-history file existed, which stops being answerable at all once history
     * persistence is optional and the memories live on the owner's client — the server can see
     * neither. A flag on the entity is true wherever either of those ends up living.
     */
    private boolean metOwner = false;

    /**
     * Skin, tracked so the client can draw each companion differently.
     *
     * <p>The filename travels rather than the roster name: the client then needs only the PNG in its
     * own {@code config/aicompanion/skins/}, never the server's config. That is already how skins
     * work — they have always been a client-side asset.
     */
    private static final TrackedData<String> SKIN_FILE =
            DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SKIN_SLIM =
            DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * Mojang {@code textures} blob when this companion borrows a player's skin, else blank.
     *
     * <p>Resolved once on the server (see {@link SkinProfileResolver}) and pushed from here, so no
     * client ever contacts Mojang and — unlike {@link #SKIN_FILE} — nothing has to be installed on
     * each machine. Arriving late is fine: tracked data syncs on change, so the companion is drawn
     * with its fallback for the moment the lookup takes and then becomes itself.
     */
    private static final TrackedData<String> SKIN_TEXTURE =
            DataTracker.registerData(CompanionEntity.class, TrackedDataHandlerRegistry.STRING);


    public CompanionEntity(EntityType<? extends CompanionEntity> type, World world) {
        super(type, world);
        this.setStepHeight(0.6f);
        setMovementSpeed(0.4f);
        this.interactionManager = new LivingEntityInteractionManager(this);
        this.inventory = new LivingEntityInventory(this);
        this.hungerManager = new LivingEntityHungerManager();
        // Runs for a fresh spawn and for one restored from a save, so a companion created before the
        // combat.* block existed picks the new values up on its next load rather than keeping the
        // zombie stat line forever. NBT is read after this and overwrites current health, as it should.
        applyCombatConfig();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SKIN_FILE, "");
        this.dataTracker.startTracking(SKIN_SLIM, false);
        this.dataTracker.startTracking(SKIN_TEXTURE, "");
    }

    /** Which roster entry this companion is, or blank if it predates the roster. */
    public String getRosterName() {
        return this.rosterName;
    }

    /** Skin PNG filename for the client renderer; blank = the default Steve texture. */
    public String getSkinFile() {
        return this.dataTracker.get(SKIN_FILE);
    }

    /** Whether to draw this companion with 3px (Alex) arms. */
    public boolean isSkinSlim() {
        return this.dataTracker.get(SKIN_SLIM);
    }

    /** Mojang textures blob for a username-sourced skin; blank = there is no such skin to draw. */
    public String getSkinTexture() {
        return this.dataTracker.get(SKIN_TEXTURE);
    }

    /**
     * Bind this companion to a roster entry: its name above its head, its skin, and the identity its
     * brain will be rebuilt from. A null entry (a name that has been deleted from the config since
     * this companion was spawned) leaves everything as it is rather than silently reverting it to the
     * default identity — the body keeps the name you gave it and the config edit can be corrected.
     */
    public void applyRosterEntry(CompanionConfig.RosterEntry entry) {
        if (entry == null) {
            return;
        }
        this.rosterName = entry.name();
        // The WHOLE identity, kept on the entity. It used to be re-resolved from the server's
        // config by name on every load and every reload, which was fine while the operator owned
        // the roster — and is exactly wrong now that the player does: the moment its owner logs off,
        // a lookup in the server's file finds nothing, or worse finds the operator's companion of
        // the same name and quietly swaps identities.
        this.identity = entry;
        this.setCustomName(Text.literal(entry.name()));
        this.setCustomNameVisible(true);
        this.dataTracker.set(SKIN_FILE, entry.skinFile() == null ? "" : entry.skinFile());
        this.dataTracker.set(SKIN_SLIM, entry.skinSlim());
        applyUsernameSkin(entry);
    }

    /** This companion's own identity, or null for one saved before identity was persisted. */
    public CompanionConfig.RosterEntry identity() {
        return this.identity;
    }

    /**
     * Kick off the Mojang lookup for a username-sourced skin, if this entry asks for one.
     *
     * <p>A local PNG wins outright: it is an explicit override, and resolving anyway would spend a
     * lookup on a result the renderer would never draw. Clearing the blob first matters for
     * {@code /companion reload} — an entry edited from a username to a file must stop drawing the old
     * player's face rather than keeping it until the next restart.
     *
     * <p>The lookup is asynchronous and may simply never call back (offline-mode server, no internet,
     * a typo'd name). That is the designed outcome, not a failure to handle: the blob stays blank and
     * the renderer falls through to the file, then to the default.
     */
    private void applyUsernameSkin(CompanionConfig.RosterEntry entry) {
        this.dataTracker.set(SKIN_TEXTURE, "");
        String username = entry.skinUsername();
        if (username == null || username.isBlank() || !entry.skinFile().isBlank()) {
            return;
        }
        SkinProfileResolver.resolve(this.getWorld().getServer(), username, blob -> {
            // The callback is scheduled onto the server thread, but a companion can be despawned
            // while a lookup is in flight.
            if (!this.isRemoved()) {
                this.dataTracker.set(SKIN_TEXTURE, blob);
                // The profile's own metadata is authoritative for arm width once a username is in
                // play — a config `slim` alongside a username would otherwise fight it.
                this.dataTracker.set(SKIN_SLIM, SkinProfileResolver.isSlim(blob));
            }
        });
    }

    /**
     * Push the {@code combat.*} config onto this companion's attributes.
     *
     * <p>Called at spawn and again on {@code /companion reload}, so retuning combat does not need a
     * restart. Sets base values rather than adding modifiers: equipment modifiers stack on top of the
     * base and would be double-counted if this ran twice against a modifier.
     *
     * <p>Health is clamped rather than left dangling — lowering {@code maxHealth} below a companion's
     * current health would otherwise leave it displaying more hearts than it has.
     */
    public void applyCombatConfig() {
        setBase(EntityAttributes.GENERIC_ATTACK_DAMAGE, CombatConfig.attackDamageBase);
        setBase(EntityAttributes.GENERIC_ARMOR, CombatConfig.armorBase);
        setBase(EntityAttributes.GENERIC_MAX_HEALTH, CombatConfig.maxHealth);
        setBase(EntityAttributes.GENERIC_FOLLOW_RANGE, CombatConfig.followRange);
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    /** Set one attribute's base value, ignoring attributes this entity somehow doesn't have. */
    private void setBase(EntityAttribute attribute, double value) {
        EntityAttributeInstance instance = this.getAttributeInstance(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Actually gain food from eating.
     *
     * <p>{@code LivingEntity.eatFood} consumes the stack and applies the eat effects but never touches
     * a food level — filling the bar is a {@code PlayerEntity} override, and a companion is not a
     * player. Without this, every route to eating (the {@code eat} command, {@code FoodChain}'s
     * auto-eat, a player right-clicking food into it) destroys the item for nothing.
     *
     * <p>It went unnoticed because hunger could never fall: regeneration ran without ever spending
     * exhaustion, so the bar sat at 20/20 and every caller refused to eat at all. The moment food
     * started draining, this became the difference between a companion that feeds itself and one that
     * eats its entire supply one item at a time and stays hungry.
     */
    @Override
    public ItemStack eatFood(World world, ItemStack stack) {
        this.hungerManager.eat(stack.getItem(), stack);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 0.5f, world.getRandom().nextFloat() * 0.1f + 0.9f);
        return super.eatFood(world, stack);
    }

    /** This companion's display name, falling back to the default identity's if it somehow has none. */
    public String displayName() {
        return this.getCustomName() != null ? this.getCustomName().getString() : CompanionConfig.name();
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
        this.rosterName = tag.getString("RosterName");
        this.metOwner = tag.getBoolean("MetOwner");
        // Identity from the entity itself, not from whatever config happens to be loaded. A
        // companion saved before identities were persisted has no Identity tag, so it falls back to
        // the old name lookup — which is exactly what it did before, and the only case where the
        // server's roster still gets a say in who somebody else's companion is.
        if (tag.contains("Identity", 10)) {
            applyRosterEntry(readIdentity(tag.getCompound("Identity")));
        } else {
            CompanionConfig.find(this.rosterName).ifPresent(this::applyRosterEntry);
        }
    }

    /** One roster entry as NBT, so a companion carries its own identity through a save. */
    private static NbtCompound writeIdentity(CompanionConfig.RosterEntry entry) {
        NbtCompound tag = new NbtCompound();
        tag.putString("name", entry.name() == null ? "" : entry.name());
        tag.putString("description", entry.description() == null ? "" : entry.description());
        tag.putString("persona", entry.persona() == null ? "" : entry.persona());
        tag.putString("skinFile", entry.skinFile() == null ? "" : entry.skinFile());
        tag.putString("skinUsername", entry.skinUsername() == null ? "" : entry.skinUsername());
        tag.putBoolean("skinSlim", entry.skinSlim());
        tag.putString("voice", entry.voice() == null ? "" : entry.voice());
        return tag;
    }

    private static CompanionConfig.RosterEntry readIdentity(NbtCompound tag) {
        return new CompanionConfig.RosterEntry(
                tag.getString("name"), tag.getString("description"), tag.getString("persona"),
                tag.getString("skinFile"), tag.getString("skinUsername"),
                tag.getBoolean("skinSlim"), tag.getString("voice"));
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
        tag.putString("RosterName", this.rosterName == null ? "" : this.rosterName);
        if (this.identity != null) {
            tag.put("Identity", writeIdentity(this.identity));
        }
        // Read back off the brain when there is one: onGreeting flips it there, and the entity is
        // what outlives the brain. Without this the flag would reset on every restart and the
        // companion would introduce itself to its owner forever.
        AltoClefController ctrl = this.controller;
        boolean met = this.metOwner
                || (ctrl != null && ctrl.getAIPersistantData() != null
                        && ctrl.getAIPersistantData().hasMetOwner());
        tag.putBoolean("MetOwner", met);
    }

    /** Consecutive AI updates that ended in an exception. Reset by any tick that completes. */
    /** Last tick's position, for charging movement exhaustion. Null until the first tick. */
    private Vec3d lastExhaustionPos;
    /** Whether it was airborne last tick, so a jump is charged once rather than every airborne tick. */
    private boolean wasOnGroundForExhaustion = true;

    /**
     * Charge exhaustion for moving about, the way {@code PlayerEntity#updateStatistics} does.
     *
     * <p>Without this a companion is never hungry. Healing was the only thing in the whole mod that
     * ever called {@code addExhaustion}, so one that stayed unhurt could cross the world, mine out a
     * hillside and win a string of fights for free — a playtest showed saturation pinned at exactly
     * 10.0 across eight minutes of hard work, which is also why automatic eating had never once had a
     * reason to fire.
     *
     * <p>Vanilla numbers, and vanilla's odd shape: ordinary walking is free, and only sprinting,
     * swimming and jumping actually cost anything. Baritone sprints and jumps constantly, so this is
     * the difference between hunger being a real resource and being decorative.
     */
    private void tickExhaustion() {
        Vec3d now = this.getPos();
        if (this.lastExhaustionPos != null) {
            double dx = now.x - this.lastExhaustionPos.x;
            double dy = now.y - this.lastExhaustionPos.y;
            double dz = now.z - this.lastExhaustionPos.z;
            // Centimetres, as vanilla measures it, so the per-metre rates below read the same as
            // PlayerEntity's.
            int cm = Math.round(MathHelper.sqrt((float) (dx * dx + dy * dy + dz * dz)) * 100.0f);
            if (cm > 0) {
                if (this.isSwimming()) {
                    this.hungerManager.addExhaustion(0.01f * cm * 0.01f);
                } else if (this.isSubmergedInWater() || this.isTouchingWater()) {
                    this.hungerManager.addExhaustion(0.01f * cm * 0.01f);
                } else if (this.isOnGround() && this.isSprinting()) {
                    this.hungerManager.addExhaustion(0.1f * cm * 0.01f);
                }
            }
        }
        this.lastExhaustionPos = now;

        // Charge the take-off, not the flight: an airborne tick is not a fresh jump.
        boolean onGround = this.isOnGround();
        if (this.wasOnGroundForExhaustion && !onGround && this.getVelocity().y > 0.0) {
            this.hungerManager.addExhaustion(this.isSprinting() ? 0.2f : 0.05f);
        }
        this.wasOnGroundForExhaustion = onGround;
    }

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

    /**
     * When this entity last ticked, in wall-clock milliseconds. See {@link #isTicking()}.
     *
     * <p>Volatile because it is written on the server thread and read from command handling.
     */
    private volatile long lastTickMs = 0L;

    /**
     * Whether this companion is actually being ticked right now.
     *
     * <p><b>Why this is measured rather than inferred.</b> Minecraft stops ticking an entity outside
     * the players' simulation distance, and a companion that is not ticking cannot run a task — which
     * means it cannot walk back. Observed 2026-08-17: sent to gather wheat seeds, Luna pathed to 198
     * blocks away with simulation distance at 12 chunks (192 blocks), and then sat there. Four
     * {@code /companion come} commands over nineteen minutes each set a fresh pathfinding task, printed
     * "Luna coming to …", and changed her distance by less than two blocks. Despawning her was the only
     * way out.
     *
     * <p>Distance to the owner would be a proxy for this, and a bad one: the real condition is "is this
     * entity receiving ticks", which also goes false for an unloaded chunk, a suspended world, or
     * anything else that arrives later. Asking the entity when it last ran answers the actual question.
     *
     * <p>The threshold is a second — twenty ticks — so an ordinary lag spike never reads as frozen.
     */
    public boolean isTicking() {
        return lastTickMs != 0L && System.currentTimeMillis() - lastTickMs < 1000L;
    }

    /** How long since this companion last ticked, in milliseconds, or -1 if it never has. */
    public long millisSinceTick() {
        return lastTickMs == 0L ? -1L : System.currentTimeMillis() - lastTickMs;
    }

    // --- Ticking: drive the managers; the controller is guarded until the nav step ---
    @Override
    public void tick() {
        lastTickMs = System.currentTimeMillis();
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
                } else if (needsBrainlessTick()) {
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
            // Order matters: bank this tick's exertion before the hunger manager converts exhaustion
            // into saturation and food, so effort is paid for in the same tick it happens.
            tickExhaustion();
            // Every tick, not age-gated: the regen timer counts ticks. Outside the brain gate too,
            // so a companion with no controller attached still heals — and still gets hungry doing it.
            this.hungerManager.tickCompanion(this);
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
        // Id and name first: with more than one companion out, the client keys its snapshots on the
        // id and labels the markers with the name.
        buf.writeVarInt(this.getId());
        buf.writeString(displayName());
        buf.writeDouble(this.getX());
        buf.writeDouble(this.getY());
        buf.writeDouble(this.getZ());
        buf.writeIdentifier(this.getWorld().getRegistryKey().getValue());
        buf.writeFloat(this.getHealth());
        buf.writeFloat(this.getMaxHealth());
        // Hunger rides the same snapshot rather than getting a channel of its own: it changes on the
        // same timescale, it is wanted at the same moments, and one packet every 10 ticks is already
        // being sent. See CompanionStatusHud.
        buf.writeVarInt(this.hungerManager.getFoodLevel());
        buf.writeFloat(this.hungerManager.getSaturationLevel());
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
        // ⚠️ Say nothing when the owner is paying for their own turns. These counters are static and
        // therefore per-JVM: with llm.clientBrain on, the call is made on the owner's machine and
        // this side stays at zero for the whole session. Sending it anyway does not merely add
        // nothing — the client's HUD takes each packet as the truth and overwrites the real figures
        // it already has, so a session that spent 101,101 tokens showed zero throughout.
        //
        // The client feeds its own panel in that case (CompanionTokenHud#selfUpdate). When it cannot
        // think and this side answers instead, this side is the one paying and the packet is right
        // again — which is why the test is "who is thinking" rather than a config flag.
        if (adris.altoclef.player2api.brain.NetworkBrainTransport.canThink(this.ownerUuid)) {
            return;
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

    /**
     * Whether a brainless companion is worth ticking Baritone for.
     *
     * <p>A companion whose owner is offline keeps no brain — {@code maintainBrain} refuses to attach
     * one rather than adopt whoever is nearby — so it falls into the brainless branch and stays there
     * for as long as that player is away. Which is for ever, in practice: companions are entities in
     * the world save, so one spawned months ago and forgotten is still here, still ticking.
     *
     * <p>That tick is not free. {@code InventoryBehavior.onTickServer} walks the companion's
     * inventory looking for a throwaway block and the best pickaxe <em>every tick</em>, and
     * {@code PathingBehavior} does its own bookkeeping, all so that a companion nobody can currently
     * command is ready to receive a path. Twenty abandoned companions is twenty of those, twenty
     * times a second, on the server thread.
     *
     * <p>⚠️ It is not simply "skip when the owner is offline", because an operator <em>can</em> still
     * command someone else's companion, and a goal set that way has to execute. So the test is
     * whether it is actually going anywhere: an idle one is skipped, one with a path or a goal keeps
     * ticking until it arrives.
     */
    private boolean needsBrainlessTick() {
        if (this.ownerUuid == null) {
            return true; // console-spawned and ownerless: nothing else will ever drive it
        }
        MinecraftServer server = this.getWorld().getServer();
        if (server != null && server.getPlayerManager().getPlayer(this.ownerUuid) != null) {
            return true; // owner is here — a brain is moments away, keep it responsive
        }
        try {
            var pathing = IBaritone.KEY.get(this).getPathingBehavior();
            return pathing.isPathing() || pathing.getGoal() != null;
        } catch (Throwable e) {
            // Never let a liveness optimisation be the thing that freezes a companion.
            return true;
        }
    }

    /** Attach the agent brain (AltoClef controller) to this companion, owned by {@code owner}. */
    public void initBrain(Character character, PlayerEntity owner) {
        this.controller = new AltoClefController(IBaritone.KEY.get(this), character, "aicompanion");
        this.controller.setOwner(owner);
        this.ownerUuid = owner.getUuid();
        // The brain is rebuilt on every load; whether they have met is not. Push it in here, or the
        // companion greets its owner as a stranger every time the chunk reloads.
        if (this.controller.getAIPersistantData() != null) {
            this.controller.getAIPersistantData().setMetOwner(this.metOwner);
        }
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
            // Its own roster entry, so a restored companion comes back as itself rather than as the
            // default identity. entryFor also matches on display name, which is what lets a body
            // spawned before the roster existed re-attach as the right character. An entry that has
            // since been deleted from the config falls back to the default.
            CompanionConfig.RosterEntry entry = CompanionConfig.entryFor(this);
            initBrain(CompanionConfig.character(
                    entry != null ? entry : CompanionConfig.defaultEntry()), owner);
            AiCompanion.LOGGER.info("[{}] re-attached brain to restored companion {} (owner {})",
                    AiCompanion.MOD_ID, displayName(), owner.getName().getString());
        } else if (this.controller.getOwner() != owner) {
            this.controller.setOwner(owner);
        }
    }

    public AltoClefController getController() {
        return this.controller;
    }

    /** Who owns this companion, or null if it was spawned without an owner. */
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    /**
     * Right-click with an empty hand to open the companion's inventory.
     *
     * <p>The only hands-on way to give it anything. Until now items could reach it only by being
     * dropped on the floor for {@link #pickupItems()} to find, or by asking the model to go and get
     * them — and armour had no route at all, despite the companion wearing and wearing out whatever
     * is in those slots.
     *
     * <p>Empty hand specifically, so holding something keeps every other right-click interaction
     * (feeding it, a future leash, whatever comes later) free. Owner-only: this is the whole of
     * someone's kit, and a companion is left standing around unattended.
     */
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isEmpty() || player.isSneaking()) {
            return super.interact(player, hand);
        }
        if (this.getWorld().isClient) {
            // Swing and open on the server's say-so; the client cannot know who the owner is.
            return ActionResult.SUCCESS;
        }
        if (this.ownerUuid != null && !this.ownerUuid.equals(player.getUuid())) {
            player.sendMessage(Text.literal(displayName() + " belongs to " + ownerName() + ".")
                    .formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        player.openHandledScreen(new CompanionScreenHandlerFactory(this));
        return ActionResult.CONSUME;
    }

    /**
     * The owner's name for a refusal message, or "someone else" if they are offline.
     *
     * <p>Public so {@code /companion} refuses in the same words the inventory screen does. Two
     * wordings for one rule is how a player learns that one of them means something different.
     */
    public String ownerName() {
        MinecraftServer server = this.getWorld().getServer();
        if (server == null || this.ownerUuid == null) {
            return "someone else";
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(this.ownerUuid);
        return owner != null ? owner.getName().getString() : "someone else";
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

    /**
     * Ticks between full-strength swings, from the {@code GENERIC_ATTACK_SPEED} attribute — the same
     * formula as {@code PlayerEntity#getAttackCooldownProgressPerTick}. A held weapon's attack-speed
     * modifier reaches the attribute via {@code detectEquipmentUpdates}, because {@link
     * #getEquippedStack} is backed by the real inventory.
     *
     * <p>Falls back to the engine's old fixed cadence if the attribute somehow isn't registered, so a
     * missing attribute degrades to the previous behaviour instead of throwing inside a tick.
     */
    public float getAttackCooldownProgressPerTick() {
        if (!this.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            return 5.0F;
        }
        return (float) (1.0D / this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED) * 20.0D);
    }

    /** 0.0 just after a swing, 1.0 once the weapon's cooldown has fully recharged. */
    public float getAttackCooldownProgress(float baseTime) {
        return MathHelper.clamp((lastAttackedTicks + baseTime) / this.getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
    }

    // --- Combat: LivingEntity has no attack of its own ---
    @Override
    public boolean tryAttack(Entity target) {
        // Swinging costs the same 0.1 exhaustion it costs a player. See tickExhaustion().
        this.hungerManager.addExhaustion(0.1f);
        // Read the cooldown before resetting it: an attack landed mid-recharge does reduced damage,
        // exactly like a player spam-clicking. Without this the companion out-DPSes its own gear.
        float charge = this.getAttackCooldownProgress(0.5F);
        lastAttackedTicks = 0;
        float damage = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float knockback = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_KNOCKBACK);
        float enchantBonus = 0.0F;
        if (target instanceof LivingEntity living) {
            enchantBonus = EnchantmentHelper.getAttackDamage(this.getMainHandStack(), living.getGroup());
            knockback += EnchantmentHelper.getKnockback(this);
        }
        damage *= 0.2F + charge * charge * 0.8F;
        enchantBonus *= charge;
        damage += enchantBonus;
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
            // Weapon wear. LivingEntity never does this — only PlayerEntity#attack calls postHit, which
            // is the hook SwordItem/AxeItem/TridentItem use for hurtAndBreak(1) and their on-hit extras.
            // Use the Item overload: the ItemStack one demands a PlayerEntity we don't have.
            if (target instanceof LivingEntity living) {
                ItemStack weapon = this.getMainHandStack();
                if (!weapon.isEmpty()) {
                    weapon.getItem().postHit(weapon, living, this);
                    if (weapon.isEmpty()) {
                        this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }
                }
            }
        }
        return hit;
    }

    /**
     * Armour durability. {@code LivingEntity#damageArmor} is an empty method — only PlayerEntity
     * overrides it — so without this the companion's armour absorbs damage forever and never breaks.
     * {@link LivingEntityInventory#damageArmor} was already written for this and had no caller; it
     * applies vanilla's {@code amount / 4, minimum 1} rule internally.
     *
     * <p>The slot indices are spelled out rather than borrowed from {@code PlayerInventory.ARMOR_SLOTS}
     * to keep this file free of a constant that has to be re-checked on every mapping bump.
     */
    @Override
    public void damageArmor(DamageSource source, float amount) {
        getLivingInventory().damageArmor(source, amount, new int[]{0, 1, 2, 3});
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

    /**
     * Wear out a shield that blocked a hit.
     *
     * <p>{@code LivingEntity.damageShield} is an empty stub — the real implementation lives on
     * {@code PlayerEntity}, so a companion blocked damage exactly as a player does but its shield never
     * lost a point of durability and could never break. That is a permanent advantage no player has,
     * and an unbreakable shield is worth considerably more than the one you handed over.
     *
     * <p>Mirrors the vanilla player rule: hits under 3 damage cost nothing, anything above costs
     * {@code 1 + floor(damage)}.
     */
    @Override
    public void damageShield(float amount) {
        if (!this.activeItemStack.isOf(Items.SHIELD) || amount < 3.0F) {
            return;
        }
        Hand hand = this.getActiveHand();
        this.activeItemStack.damage(1 + MathHelper.floor(amount), this,
                companion -> companion.sendToolBreakStatus(hand));
        if (this.activeItemStack.isEmpty()) {
            this.equipStack(hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND,
                    ItemStack.EMPTY);
            this.clearActiveItem();
            this.playSound(SoundEvents.ITEM_SHIELD_BREAK, 0.8F,
                    0.8F + this.getWorld().getRandom().nextFloat() * 0.4F);
        }
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
