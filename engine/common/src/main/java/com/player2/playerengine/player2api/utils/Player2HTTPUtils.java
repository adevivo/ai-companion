package com.player2.playerengine.player2api.utils;

import com.neovetta.aicompanion.core.LlmConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class Player2HTTPUtils {
    public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, boolean postRequest, JsonObject requestBody) throws Exception{
        return sendRequest(player, clientId, endpoint, postRequest, requestBody, null);
    }

    /**
     * As above, with request-specific headers merged in on top of the auth ones.
     *
     * <p>Exists for xAI's {@code x-grok-conv-id} routing header, which is not authentication and is
     * meaningless to the other endpoints this method serves. Unknown headers are ignored by
     * llama.cpp and the other OpenAI-compatible servers, so it costs nothing to send it to all of
     * them rather than special-casing the provider here.
     */
    public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, boolean postRequest, JsonObject requestBody, Map<String, String> extraHeaders) throws Exception{
        // Always the user-configured OpenAI-compatible endpoint (llama.cpp, Ollama, OpenAI, xAI, ...).
        // Upstream's Player2 cloud path (device auth, api.player2.game, the 60 s heartbeat) was removed
        // 2026-10-08: CurseForge rejected the bundled engine for it, and nothing we ship used it.
        // `player` and `clientId` are unused now; kept so the call sites stay unchanged.
        Map<String, String> headers = null;
        if (LlmConfig.apiKey != null && !LlmConfig.apiKey.isBlank()) {
            headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + LlmConfig.apiKey);
        }
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            if (headers == null) {
                headers = new HashMap<>();
            }
            headers.putAll(extraHeaders);
        }
        return HTTPUtils.sendRequest(LlmConfig.baseUrl, endpoint, postRequest, requestBody, headers);
    }
}
