package com.neovetta.aicompanion.client;

import com.neovetta.aicompanion.AiCompanion;
import com.neovetta.aicompanion.CompanionConfig;
import com.mojang.blaze3d.texture.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
