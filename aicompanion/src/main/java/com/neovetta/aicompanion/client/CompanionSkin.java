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

/**
 * Client-side loader for a custom companion skin. Reads the PNG named in
 * {@code companion.skin.file} from {@link CompanionConfig#skinsDir()} and registers it as a dynamic
 * texture the first time the renderer needs it (on the render thread). All Minecraft client texture
 * classes live here so nothing client-only leaks into the common {@link CompanionConfig}.
 */
public final class CompanionSkin {

    private static boolean attempted = false;
    private static Identifier loaded = null;

    private CompanionSkin() {}

    /** The configured skin texture if it loaded, else {@code fallback}. Loads once, then caches. */
    public static Identifier textureOrDefault(Identifier fallback) {
        if (!attempted) {
            attempted = true;
            loaded = tryLoad();
        }
        return loaded != null ? loaded : fallback;
    }

    private static Identifier tryLoad() {
        String file = CompanionConfig.skinFile();
        if (file == null || file.isBlank()) {
            return null;
        }
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
