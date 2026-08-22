package com.neovetta.aicompanion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Turns a Mojang username into the {@code textures} blob that lets a client draw that player's skin.
 *
 * <p><b>Server-side on purpose.</b> Resolving a name means a round trip to Mojang (name → UUID →
 * profile properties). Doing it on each client would be one lookup per player per companion, with the
 * rate-limit exposure and the inconsistent results that implies. Resolving once here and pushing the
 * result to clients as tracked data means <em>no client ever talks to Mojang</em> — and it is what
 * makes username skins work on a LAN, where file skins need the PNG copied to every machine.
 *
 * <p>The work is done by {@link SkullBlockEntity#loadProperties}, which is vanilla's own name→profile
 * resolver — the one player heads use. It relies on the services {@code MinecraftServer} installs at
 * startup, so there is nothing to stand up here.
 *
 * <p>The blob itself is base64 JSON of the form
 * {@code {"textures":{"SKIN":{"url":"...","metadata":{"model":"slim"}}}}} — around 300 characters,
 * comfortably inside the 32767-character tracked-data string limit.
 */
public final class SkinProfileResolver {

    /**
     * username (lowercased) → textures blob. A resolved-but-skinless or failed lookup caches
     * {@link #NO_SKIN} so it is never retried; without that, a typo'd username would hit Mojang on
     * every spawn forever.
     */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /** Lookups already in progress, so two companions naming the same player make one request. */
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** Cache sentinel for "asked, and there is no skin to be had". Not a valid blob. */
    private static final String NO_SKIN = "";

    private SkinProfileResolver() {}

    /**
     * Resolve {@code username} and hand the textures blob to {@code onResolved} <em>on the server
     * thread</em>, or do nothing at all if there is no skin to be found.
     *
     * <p>Deliberately silent on failure beyond one log line: an unresolvable username (offline-mode
     * server, no internet, a typo) must leave the companion looking like it did before rather than
     * breaking the spawn. The caller's fallback chain — local PNG, then default — does the rest.
     */
    public static void resolve(MinecraftServer server, String username, Consumer<String> onResolved) {
        if (server == null || username == null || username.isBlank()) {
            return;
        }
        String key = username.strip().toLowerCase();

        String cached = CACHE.get(key);
        if (cached != null) {
            if (!cached.equals(NO_SKIN)) {
                server.execute(() -> onResolved.accept(cached));
            }
            return;
        }
        // Two companions sharing a username should cost one lookup, not two. The loser simply gets
        // nothing this time; the next spawn reads it out of the cache.
        if (!IN_FLIGHT.add(key)) {
            return;
        }

        try {
            SkullBlockEntity.loadProperties(new GameProfile(null, username.strip()), profile -> {
                String blob = texturesBlob(profile).orElse(NO_SKIN);
                CACHE.put(key, blob);
                IN_FLIGHT.remove(key);
                if (blob.equals(NO_SKIN)) {
                    AiCompanion.LOGGER.warn("[{}] no skin found for username '{}' — falling back",
                            AiCompanion.MOD_ID, username);
                    return;
                }
                // loadProperties calls back off-thread; tracked data may only be touched on the
                // server thread.
                server.execute(() -> onResolved.accept(blob));
            });
        } catch (Exception e) {
            // An offline-mode server has no session service to ask. That is a supported setup, not an
            // error worth breaking a spawn over.
            IN_FLIGHT.remove(key);
            CACHE.put(key, NO_SKIN);
            AiCompanion.LOGGER.warn("[{}] could not look up username '{}': {}",
                    AiCompanion.MOD_ID, username, e.toString());
        }
    }

    /** Forget every cached lookup, so {@code /companion reload} can pick up a corrected username. */
    public static void clearCache() {
        CACHE.clear();
        IN_FLIGHT.clear();
    }

    /** The {@code textures} property value from a filled profile, if it carries one. */
    private static Optional<String> texturesBlob(GameProfile profile) {
        if (profile == null) {
            return Optional.empty();
        }
        Collection<Property> textures = profile.getProperties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return Optional.empty();
        }
        String value = textures.iterator().next().getValue();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    // ---- blob decoding -------------------------------------------------------------------------
    // Both sides need this: the server to decide arm width, the client to find the PNG to download.
    // It lives here rather than in the client-only skin loader so nothing client-only leaks server-side.

    /** The skin PNG URL inside a textures blob, or empty if the blob is malformed or has no skin. */
    public static Optional<String> textureUrl(String blob) {
        return skinObject(blob)
                .map(skin -> skin.has("url") ? skin.get("url").getAsString() : null)
                .filter(url -> url != null && !url.isBlank());
    }

    /**
     * Whether the blob asks for the 3px (Alex) arm model.
     *
     * <p>Classic skins carry no {@code metadata} at all, so absence means wide — which is why this
     * returns a plain boolean rather than an Optional.
     */
    public static boolean isSlim(String blob) {
        return skinObject(blob)
                .map(skin -> skin.getAsJsonObject("metadata"))
                .map(meta -> meta.has("model") && "slim".equalsIgnoreCase(meta.get("model").getAsString()))
                .orElse(false);
    }

    /** The {@code textures.SKIN} object out of a base64 blob. Empty for anything unparseable. */
    private static Optional<JsonObject> skinObject(String blob) {
        if (blob == null || blob.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = new String(Base64.getDecoder().decode(blob), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject textures = parsed.getAsJsonObject().getAsJsonObject("textures");
            if (textures == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(textures.getAsJsonObject("SKIN"));
        } catch (Exception e) {
            // Malformed blobs are a data problem, not a crash. Callers fall back to the local PNG.
            return Optional.empty();
        }
    }
}
