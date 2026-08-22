package com.neovetta.aicompanion;

import adris.altoclef.player2api.PlayerPreferences;
import adris.altoclef.player2api.RosterGuard;
import adris.altoclef.player2api.ServerPolicy;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * What each connected player's client says about itself: its companions, and its trigger prefix.
 *
 * <h2>Why identity had to move</h2>
 *
 * The roster used to come from {@code config/aicompanion.json} next to the world, which is the
 * <em>operator's</em> file. On a dedicated server that meant every player got the operator's
 * companions — the operator's names, faces and personalities — and could change none of it. It also
 * meant a name was unique server-wide, so the second player to run {@code /companion spawn Vetta}
 * was told Vetta was already out, in somebody else's base, four thousand blocks away.
 *
 * <p>So the client announces its own roster at join and the server keeps it here for as long as that
 * player is connected. Ownership, not the name, is what tells two players' companions apart.
 *
 * <h2>This is untrusted input</h2>
 *
 * ⚠️ Everything arriving here was written by whoever is connecting, and it reaches chat, the system
 * prompt and the spawn suggestion list. {@link RosterGuard} is the whole of the validation and it
 * runs before anything is stored — never store first and check later. What the guard refused is told
 * to the player rather than dropped, because a companion that does not appear needs a reason
 * attached to it.
 *
 * <p>Nothing here outlives the connection. A cache that survived a disconnect would let a player's
 * roster affect a server they had left, and would grow without bound.
 */
public final class ClientProfiles {
    private ClientProfiles() {}

    private static final Map<UUID, List<CompanionConfig.RosterEntry>> ROSTERS =
            new ConcurrentHashMap<>();

    /**
     * Accept a roster announcement. Returns what to tell the player, or an empty list when
     * everything they sent was fine.
     *
     * @param payload the client's {@code companions} array and {@code triggerPrefix}, as sent
     */
    public static List<String> announce(ServerPlayerEntity player, JsonObject payload) {
        if (player == null || payload == null) {
            return List.of();
        }
        UUID id = player.getUuid();

        // Their prefix travels with their roster because it has the same shape of problem: it is a
        // client-owned setting that only the server can act on, since chat routing is server-side.
        PlayerPreferences.setTriggerPrefix(id,
                payload.has("triggerPrefix") && payload.get("triggerPrefix").isJsonPrimitive()
                        ? payload.get("triggerPrefix").getAsString()
                        : null);

        List<RosterGuard.Identity> announced = new ArrayList<>();
        JsonElement companions = payload.get("companions");
        if (companions != null && companions.isJsonArray()) {
            for (JsonElement el : companions.getAsJsonArray()) {
                if (el.isJsonObject()) {
                    announced.add(toIdentity(el.getAsJsonObject()));
                }
            }
        }

        RosterGuard.Result result = RosterGuard.sanitize(announced, onlineNames(player.getServer()));
        // The skills advertisement is the SERVER's to add — the skill files are its own, and a
        // client cannot know what they are. Appended on the same terms as a server-side entry: only
        // to a persona that is set, because a blank one falls back to the global persona, which is
        // the advertisement already and would otherwise be said twice.
        String advert = CompanionSkills.advertisement();
        List<CompanionConfig.RosterEntry> accepted = new ArrayList<>();
        for (RosterGuard.Identity identity : result.accepted()) {
            String persona = identity.persona();
            if (!persona.isBlank() && !advert.isEmpty()) {
                persona = persona + "\n\n" + advert;
            }
            accepted.add(new CompanionConfig.RosterEntry(identity.name(), identity.description(),
                    persona, identity.skinFile(), identity.skinUsername(),
                    identity.skinSlim(), identity.voice()));
        }
        if (accepted.isEmpty()) {
            // Nothing usable: drop the entry entirely so rosterFor falls back to the server's own,
            // rather than leaving them with an empty roster and no way to spawn anything.
            ROSTERS.remove(id);
        } else {
            ROSTERS.put(id, List.copyOf(accepted));
        }
        AiCompanion.LOGGER.info("[{}] {} announced {} companion(s), {} accepted{}",
                AiCompanion.MOD_ID, player.getName().getString(), announced.size(), accepted.size(),
                result.rejections().isEmpty() ? "" : " — " + String.join("; ", result.rejections()));
        return result.rejections();
    }

    /** They left. Drop everything of theirs — see the class note on why this is not a cache. */
    public static void forget(UUID player) {
        if (player != null) {
            ROSTERS.remove(player);
            PlayerPreferences.forget(player);
        }
    }

    /**
     * The identities this player may spawn.
     *
     * <p>Falls back to the server's own {@code companions} block, which is what singleplayer, a LAN
     * host, the console and a vanilla-config client all need — in every one of those cases the local
     * file <em>is</em> the player's file, or there is no player at all.
     */
    public static List<CompanionConfig.RosterEntry> rosterFor(UUID player) {
        List<CompanionConfig.RosterEntry> announced = player == null ? null : ROSTERS.get(player);
        return announced == null || announced.isEmpty() ? CompanionConfig.roster() : announced;
    }

    /** Everyone currently connected, for the impersonation check. */
    private static Set<String> onlineNames(MinecraftServer server) {
        Set<String> names = new LinkedHashSet<>();
        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                names.add(p.getName().getString());
            }
        }
        return names;
    }

    /** Read one announced entry. Missing fields are blank, never null — the guard clamps the rest. */
    private static RosterGuard.Identity toIdentity(JsonObject o) {
        return new RosterGuard.Identity(
                str(o, "name"), str(o, "description"), str(o, "persona"),
                str(o, "skinFile"), str(o, "skinUsername"),
                o.has("skinSlim") && o.get("skinSlim").isJsonPrimitive()
                        && o.get("skinSlim").getAsBoolean(),
                str(o, "voice"));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    /** The announcement a client sends, built from its own config file. */
    public static JsonObject buildAnnouncement(JsonObject localConfig) {
        JsonObject out = new JsonObject();
        JsonArray companions = new JsonArray();
        if (localConfig != null && localConfig.has("companions")
                && localConfig.get("companions").isJsonArray()) {
            JsonArray source = localConfig.getAsJsonArray("companions");
            for (JsonElement el : source) {
                if (!el.isJsonObject()) {
                    continue;
                }
                // Only the identity fields. The rest of a client's config is its own business and
                // there is no reason for a server to see any of it — least of all the API key that
                // lives two blocks away in the same file.
                //
                // ⚠️ Read in the file's own shape, not the wire's: a config entry spells persona
                // as "systemPrompt" and carries skin as a nested object (or a bare filename
                // string). Reading flat "persona"/"skinFile" keys here finds nothing and announces
                // every companion with no personality and no face — which looks like the feature
                // working, because the name and description come through fine.
                JsonObject in = el.getAsJsonObject();
                JsonObject entry = new JsonObject();
                entry.addProperty("name", str(in, "name"));
                entry.addProperty("description", str(in, "description"));
                entry.addProperty("persona", str(in, "systemPrompt"));
                entry.addProperty("voice", str(in, "voice"));

                String file = "";
                String username = "";
                boolean slim = false;
                JsonElement skin = in.get("skin");
                if (skin != null && skin.isJsonPrimitive()) {
                    file = skin.getAsString();
                } else if (skin != null && skin.isJsonObject()) {
                    JsonObject s = skin.getAsJsonObject();
                    file = str(s, "file");
                    username = str(s, "username");
                    slim = s.has("slim") && s.get("slim").isJsonPrimitive()
                            && s.get("slim").getAsBoolean();
                }
                entry.addProperty("skinFile", file);
                entry.addProperty("skinUsername", username);
                entry.addProperty("skinSlim", slim);
                companions.add(entry);
                if (!ServerPolicy.withinCap(companions.size() - 1, ServerPolicy.maxRosterEntries)) {
                    // Trimmed here too so an oversized roster is not sent at all. The server clamps
                    // regardless; this only keeps the packet honest.
                    break;
                }
            }
        }
        out.add("companions", companions);
        JsonObject behavior = localConfig == null ? null
                : (localConfig.has("behavior") && localConfig.get("behavior").isJsonObject()
                        ? localConfig.getAsJsonObject("behavior") : null);
        out.addProperty("triggerPrefix", behavior == null ? "" : str(behavior, "triggerPrefix"));
        return out;
    }
}
