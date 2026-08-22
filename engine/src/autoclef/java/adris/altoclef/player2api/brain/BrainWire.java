package adris.altoclef.player2api.brain;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * The wire contract between a game server and the client that does its companion's thinking.
 *
 * <h2>Why the payload is a byte array and not a string</h2>
 *
 * {@code writeUtf} caps at 32767 <b>characters</b> by default. A prompt is bounded by
 * {@code llm.maxPromptChars}, which the player can raise — it was raised from 16000 to 20000 on
 * 2026-08-19 — so a string field would put a silent ceiling on a user-tunable setting and fail only
 * for players who tuned it up. UTF-8 bytes go through {@code writeByteArray} instead, and the real
 * limit is the packet's: 1 MB for S2C, which a 20 KB prompt is nowhere near.
 *
 * <p>⚠️ <b>C2S is capped at 32767 bytes</b> by the vanilla serverbound custom-payload packet, which
 * is why the result carries only {@code {reason, command, message}} and never the prompt back.
 */
public final class BrainWire {

    private BrainWire() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation("aicompanion", path);
    }

    /** S2C — "think about this turn for me". Carries the ingredients, never the finished prompt. */
    public static final ResourceLocation TURN_REQUEST = id("brain_turn_request");

    /** C2S — the reply, or an error string. */
    public static final ResourceLocation TURN_RESULT = id("brain_turn_result");

    /**
     * C2S — "I can think for myself", sent once on join.
     *
     * <p>Without it the server cannot distinguish a client that is still working from a vanilla
     * client that will never answer, and would have to pay the timeout on every single turn before
     * falling back. A companion whose every reply is late by the timeout is worse than one that
     * never moved to the client at all.
     */
    public static final ResourceLocation HELLO = id("brain_hello");

    /**
     * S2C — "write this into your own corpus", for {@code /companion remember}.
     *
     * <p>The commands are Brigadier and run on the server, so they never went through the transport
     * at all. Observed 2026-08-20 with {@code clientBrain} on: {@code /companion rememberhere home}
     * wrote to the server while everything learned from conversation went to the client. The
     * memories were then split across two machines and asking where home was recalled nothing, with
     * no error anywhere — the worst shape a bug can have.
     *
     * <p>⚠️ <b>The confirmation is printed by the client, from the record as stored.</b> Reporting
     * what was submitted would claim success even when the store kept an older record and dropped
     * this one, which is what happened the first time the command shipped. The client is also the
     * only side that can count the corpus once it owns it.
     */
    public static final ResourceLocation MEMORY_REMEMBER = id("memory_remember");

    /**
     * Everything the client needs to build the prompt itself.
     *
     * <p>The server sends <b>ingredients, not a prompt</b>: raw history plus the status blobs it
     * alone can compute. The client assembles, because assembly is where memories are injected and
     * the whole point is that the player's memories never transit the server. Sending a finished
     * prompt with a slot for memories would have put them back on the wire.
     */
    public static void writeTurnRequest(FriendlyByteBuf buf, UUID requestId, UUID companionUuid,
            JsonObject context) {
        buf.writeUUID(requestId);
        buf.writeUUID(companionUuid);
        buf.writeByteArray(context.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static JsonObject readContext(FriendlyByteBuf buf) {
        return com.google.gson.JsonParser
                .parseString(new String(buf.readByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** C2S result. {@code error} non-empty means the client could not think; the server falls back. */
    public static void writeTurnResult(FriendlyByteBuf buf, UUID requestId, String replyJson,
            String error) {
        buf.writeUUID(requestId);
        buf.writeByteArray((replyJson == null ? "" : replyJson).getBytes(StandardCharsets.UTF_8));
        buf.writeUtf(error == null ? "" : error, 512);
    }

    /**
     * S2C payload for {@link #MEMORY_REMEMBER}: a fact, its scope, and where the player was standing.
     *
     * <p>The place is captured on the server thread before this is sent, because "here" means where
     * they were when they typed it, and a memory about a place that carries no place is what makes
     * the companion borrow a coordinate from elsewhere in the prompt and present it as recall.
     *
     * <p>Sent as one JSON blob rather than typed fields for the same reason the turn context is: one
     * definition of the field names, read by one method, instead of a writer and a reader that can
     * drift apart silently.
     */
    public static void writeRemember(FriendlyByteBuf buf, JsonObject request) {
        buf.writeByteArray(request.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static JsonObject readRemember(FriendlyByteBuf buf) {
        return com.google.gson.JsonParser
                .parseString(new String(buf.readByteArray(), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /**
     * Builds the remember request. {@code place} is null for a person-scoped memory, which is true
     * of the player everywhere and so belongs to no coordinate.
     */
    public static JsonObject rememberRequest(String fact, boolean thisWorldOnly, String worldId,
            String placeDimension, Integer placeX, Integer placeY, Integer placeZ) {
        JsonObject o = new JsonObject();
        o.addProperty("fact", fact == null ? "" : fact);
        o.addProperty("thisWorldOnly", thisWorldOnly);
        // Absent rather than null, so a missing field can never become the string "null" inside a
        // stored memory — see the same rule in context() below.
        if (worldId != null) {
            o.addProperty("worldId", worldId);
        }
        if (placeDimension != null && placeX != null && placeY != null && placeZ != null) {
            JsonObject place = new JsonObject();
            place.addProperty("dimension", placeDimension);
            place.addProperty("x", placeX);
            place.addProperty("y", placeY);
            place.addProperty("z", placeZ);
            o.add("place", place);
        }
        return o;
    }

    /** Builds the context object. Kept here so both sides read one definition of the field names. */
    public static JsonObject context(JsonArray messages, String worldStatus, String agentStatus,
            String debugMessages, String reminder, String turnText, String worldId,
            String companionName, String ownerName, String ownerUuid, boolean autonomous) {
        JsonObject o = new JsonObject();
        o.add("messages", messages);
        o.addProperty("worldStatus", worldStatus == null ? "" : worldStatus);
        o.addProperty("agentStatus", agentStatus == null ? "" : agentStatus);
        o.addProperty("debugMessages", debugMessages == null ? "" : debugMessages);
        o.addProperty("companionName", companionName == null ? "" : companionName);
        o.addProperty("ownerName", ownerName == null ? "" : ownerName);
        o.addProperty("autonomous", autonomous);
        // Optional: absent rather than null, so the client reads them with a presence check and a
        // missing field can never become the string "null" inside a prompt.
        if (reminder != null) {
            o.addProperty("reminder", reminder);
        }
        if (turnText != null) {
            o.addProperty("turnText", turnText);
        }
        if (worldId != null) {
            o.addProperty("worldId", worldId);
        }
        if (ownerUuid != null) {
            o.addProperty("ownerUuid", ownerUuid);
        }
        return o;
    }

    /** Reads an optional string field, or null. */
    public static String opt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    /** Reads a required string field, or "". */
    public static String str(JsonObject o, String key) {
        String v = opt(o, key);
        return v == null ? "" : v;
    }
}
