package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import com.neovetta.aicompanion.SkinProfileResolver;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.texture.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

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
     * <p>Returns immediately even on a cold cache: {@code loadSkin} hands back an identifier straight
     * away and downloads the PNG on a background thread, swapping the texture in when it lands. The
     * companion is briefly drawn with {@code fallback} and then simply becomes itself. That is the
     * whole reason this is safe to call from {@code getTexture} on every frame.
     */
    public static Identifier textureFromProfile(String blob, Identifier fallback) {
        if (blob == null || blob.isBlank()) {
            return fallback;
        }
        // containsKey, not get() != null: a blob that could not be used caches null and must not be
        // retried every frame. Same reasoning as textureOrDefault.
        if (!PROFILE_CACHE.containsKey(blob)) {
            PROFILE_CACHE.put(blob, tryLoadProfile(blob));
        }
        Identifier loaded = PROFILE_CACHE.get(blob);
        return loaded != null ? loaded : fallback;
    }

    private static Identifier tryLoadProfile(String blob) {
        Optional<String> url = SkinProfileResolver.textureUrl(blob);
        if (url.isEmpty()) {
            AiCompanion.LOGGER.warn("[{}] companion skin profile carried no usable texture URL",
                    AiCompanion.MOD_ID);
            return null;
        }
        try {
            // Arm width is not read from here: it travels separately as tracked data, decoded
            // server-side, so the metadata map this constructor takes can stay empty.
            MinecraftProfileTexture texture = new MinecraftProfileTexture(url.get(), Map.of());
            Identifier id = MinecraftClient.getInstance().getSkinProvider()
                    .loadSkin(texture, MinecraftProfileTexture.Type.SKIN);
            AiCompanion.LOGGER.info("[{}] loaded companion skin from {}", AiCompanion.MOD_ID, url.get());
            return id;
        } catch (Exception e) {
            AiCompanion.LOGGER.error("[{}] failed to load companion skin from {}: {}",
                    AiCompanion.MOD_ID, url.get(), e.toString());
            return null;
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
            Identifier id = new Identifier(AiCompanion.MOD_ID, "skin/" + safe);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                    new NativeImageBackedTexture(image));
            AiCompanion.LOGGER.info("[{}] loaded companion skin from {}", AiCompanion.MOD_ID, path);
            return id;
        } catch (Exception e) {
            AiCompanion.LOGGER.error("[{}] failed to load skin '{}': {}", AiCompanion.MOD_ID, file,
                    e.toString());
            return null;
        }
    }
}
