package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import com.neovetta.aicompanion.SkinProfileResolver;
import com.mojang.authlib.GameProfile;
import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side loader for companion skins. Reads a PNG from {@link CompanionConfig#skinsDir()} and
 * registers it as a dynamic texture the first time the renderer asks for it (on the render thread).
 * All Minecraft client texture classes live here so nothing client-only leaks into the common
 * {@link CompanionConfig}.
 *
 * <p>Keyed by filename rather than holding one texture, because each companion in the roster brings
 * its own — a single cached skin would have drawn every one of them with the same face. The filename
 * arrives on the entity itself (tracked data), so this never needs to read the server's config.
 */
public final class CompanionSkin {

    /** filename → loaded texture, or null when that file could not be loaded. Render thread only. */
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    /**
     * textures blob → registered texture, or null when the blob could not be used. Render thread only.
     *
     * <p>Separate from {@link #CACHE} because the two are keyed on different things and a username
     * skin needs no file to exist at all.
     */
    private static final Map<String, Identifier> PROFILE_CACHE = new HashMap<>();

    /** Blobs with a request in flight, so the render path asks once rather than once per frame. */
    private static final Set<String> PROFILE_PENDING = new HashSet<>();

    private CompanionSkin() {}

    /** The named skin if it loaded, else {@code fallback}. Loads once per filename, then caches. */
    public static Identifier textureOrDefault(String file, Identifier fallback) {
        if (file == null || file.isBlank()) {
            return fallback;
        }
        // containsKey, not get() != null: a failed load caches null and must not be retried every frame.
        if (!CACHE.containsKey(file)) {
            CACHE.put(file, tryLoad(file));
        }
        Identifier loaded = CACHE.get(file);
        return loaded != null ? loaded : fallback;
    }

    /**
     * The skin described by a Mojang {@code textures} blob, else {@code fallback}.
     *
     * <p>The blob arrives on the entity as tracked data, already resolved server-side by
     * {@link SkinProfileResolver} — so this never talks to Mojang and never needs a PNG on disk,
     * which is what makes username skins work for every client on a LAN.
     *
     * <p>Returns immediately even on a cold cache. The skin manager is asked once per blob and
     * answers with a future; until it completes the companion is drawn with {@code fallback} and
     * then simply becomes itself. That is the whole reason this is safe to call on every frame.
     *
     * <p>⚠️ Cold-cache behaviour changed in the 1.21.11 port and is worth knowing. The old
     * {@code SkinManager.loadSkin(MinecraftProfileTexture, Type)} handed back an identifier
     * synchronously and filled the texture in later. That entry point is gone; what remains takes a
     * {@link GameProfile} and returns a future, so the identifier itself now arrives late.
     */
    public static Identifier textureFromProfile(String blob, Identifier fallback) {
        if (blob == null || blob.isBlank()) {
            return fallback;
        }
        // containsKey, not get() != null: a blob that could not be used caches null and must not be
        // retried every frame. Same reasoning as textureOrDefault.
        if (!PROFILE_CACHE.containsKey(blob) && PROFILE_PENDING.add(blob)) {
            requestProfileSkin(blob);
        }
        Identifier loaded = PROFILE_CACHE.get(blob);
        return loaded != null ? loaded : fallback;
    }

    /**
     * Ask the skin manager for the skin a {@code textures} blob describes, and cache the answer.
     *
     * <p>The blob is handed over as a property on a synthetic {@link GameProfile}, which is what
     * the session service reads: the profile needs no real identity, because nothing is looked up
     * remotely — everything the download needs is already inside the blob, resolved server-side by
     * {@link SkinProfileResolver}. The UUID is derived from the blob so two companions sharing a
     * skin share a cache entry rather than racing for two downloads of the same PNG.
     *
     * <p>Failure caches {@code null}, and {@link #PROFILE_PENDING} keeps the entry from being
     * retried on the next frame — this is called from the render path.
     */
    private static void requestProfileSkin(String blob) {
        if (SkinProfileResolver.textureUrl(blob).isEmpty()) {
            AiCompanion.LOGGER.warn("[{}] companion skin profile carried no usable texture URL",
                    AiCompanion.MOD_ID);
            PROFILE_CACHE.put(blob, null);
            PROFILE_PENDING.remove(blob);
            return;
        }
        try {
            // ⚠️ Populate the multimap BEFORE wrapping it. Nothing here is mutable after
            // construction, at either level, and both levels throw the same bare
            // UnsupportedOperationException from the render thread:
            //
            //   GameProfile is a record, and its 2-arg constructor delegates to PropertyMap.EMPTY,
            //   a shared static. So new GameProfile(id, name).properties().put(...) throws.
            //
            //   PropertyMap's constructor does ImmutableMultimap.copyOf(...) on whatever it is
            //   given, so a PropertyMap is ALWAYS immutable no matter how mutable the multimap
            //   handed to it was. So new PropertyMap(LinkedHashMultimap.create()).put(...) throws
            //   too -- the same bug one layer down, which is exactly the trap this fell into first
            //   time round.
            //
            // The only order that works is: fill the multimap, then wrap it, then build the profile.
            //
            // This is what broke every Mojang-username skin. The throw was caught and the result
            // cached as null, so it showed up as "the fallback skin, forever" rather than as an
            // error a player would notice.
            LinkedHashMultimap<String, Property> textures = LinkedHashMultimap.create();
            textures.put("textures", new Property("textures", blob));
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(blob.getBytes(StandardCharsets.UTF_8)), "companion",
                    new PropertyMap(textures));
            Minecraft client = Minecraft.getInstance();
            client.getSkinManager().get(profile).whenComplete((skin, error) -> client.execute(() -> {
                PROFILE_PENDING.remove(blob);
                if (error != null || skin == null || skin.isEmpty()) {
                    AiCompanion.LOGGER.error("[{}] failed to load companion skin from its profile: {}",
                            AiCompanion.MOD_ID, error == null ? "no skin returned" : error.toString());
                    PROFILE_CACHE.put(blob, null);
                    return;
                }
                PROFILE_CACHE.put(blob, skin.get().body().texturePath());
                AiCompanion.LOGGER.info("[{}] loaded companion skin from its profile",
                        AiCompanion.MOD_ID);
            }));
        } catch (Exception e) {
            // Pass the exception, not e.toString(): an UnsupportedOperationException carries no
            // message, so without a stack trace this logs six identical words and nothing that
            // says which call threw. That cost two round trips of guessing.
            AiCompanion.LOGGER.error("[{}] failed to request a companion skin",
                    AiCompanion.MOD_ID, e);
            PROFILE_CACHE.put(blob, null);
            PROFILE_PENDING.remove(blob);
        }
    }

    private static Identifier tryLoad(String file) {
        Path path = CompanionConfig.skinsDir().resolve(file);
        if (!Files.exists(path)) {
            AiCompanion.LOGGER.warn("[{}] skin '{}' not found in {} — using default", AiCompanion.MOD_ID,
                    file, CompanionConfig.skinsDir());
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(in);
            // Identifier paths only allow [a-z0-9/._-]; sanitize the filename so any name is valid.
            String safe = file.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.fromNamespaceAndPath(AiCompanion.MOD_ID, "skin/" + safe);
            // DynamicTexture takes a label supplier now, used in debug output and GPU labels.
            Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(() -> "aicompanion skin " + safe, image));
            AiCompanion.LOGGER.info("[{}] loaded companion skin from {}", AiCompanion.MOD_ID, path);
            return id;
        } catch (Exception e) {
            AiCompanion.LOGGER.error("[{}] failed to load skin '{}': {}", AiCompanion.MOD_ID, file,
                    e.toString());
            return null;
        }
    }
}
