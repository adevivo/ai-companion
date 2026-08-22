package com.player2.playerengine.player2api.utils;

import com.neovetta.aicompanion.core.LlmConfig;
import com.neovetta.aicompanion.core.LlmConfig;
import com.player2.playerengine.player2api.auth.AuthKey;
import com.player2.playerengine.player2api.auth.AuthenticationManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class Player2HTTPUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String WEB_API_URL = "https://api.player2.game";

    // Track players who have already attempted reauth for 402 errors (retry once only)
    private static final Set<AuthKey> energyRetryAttempted = ConcurrentHashMap.newKeySet();

    public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, boolean postRequest, JsonObject requestBody) throws Exception{
        return sendRequest(player, clientId, endpoint, postRequest, requestBody, null);
    }

    /**
     * As above, with request-specific headers merged in on top of the auth ones.
     *
     * <p>Exists for xAI's {@code x-grok-conv-id} routing header, which is not authentication and is
     * meaningless to the other endpoints this method serves. Unknown headers are ignored by
     * llama.cpp and by the Player2 API, so it costs nothing to let it through either path rather
     * than special-casing the provider here.
     */
    public static Map<String, JsonElement> sendRequest(Player player, String clientId, String endpoint, boolean postRequest, JsonObject requestBody, Map<String, String> extraHeaders) throws Exception{
        if (LlmConfig.localMode) {
            // Local or hosted OpenAI-compatible endpoint: no Player2 device-auth/token. If an apiKey is
            // configured (e.g. xAI/Grok, OpenAI), send it as a bearer token; otherwise no auth (llama.cpp).
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

        String token = awaitToken(player, clientId);
        Map<String, String> headers = getHeaders(clientId, token);
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }

        try {
            return HTTPUtils.sendRequest(WEB_API_URL, endpoint, postRequest, requestBody, headers);
        } catch (HttpApiException e) {
            AuthKey authKey = new AuthKey(player.getUUID(), clientId);

            if (e.getStatusCode() == 401) {
                LOGGER.warn("Received 401 Unauthorized for {}. Invalidating token.", authKey);
                AuthenticationManager.getInstance().invalidateToken(player, clientId);
                throw new Exception("Token expired, re-authentication started.", e);
            }

            // Handle HTTP 402 "insufficient_credits" (out of energy) - reauth and check if account changed
            if (e.getStatusCode() == 402 && !energyRetryAttempted.contains(authKey)) {
                LOGGER.warn("Received 402 insufficient credits for {}. Attempting reauth.", authKey);
                energyRetryAttempted.add(authKey);
                String oldToken = token;
                AuthenticationManager.getInstance().invalidateToken(player, clientId);

                // Wait for reauth to complete
                String newToken = awaitToken(player, clientId);

                // If token changed (different account), retry the request
                if (!oldToken.equals(newToken)) {
                    LOGGER.info("Token changed after reauth for {}, retrying request.", authKey);
                    Map<String, String> newHeaders = getHeaders(clientId, newToken);
                    return HTTPUtils.sendRequest(WEB_API_URL, endpoint, postRequest, requestBody, newHeaders);
                }

                // Same token = same account with no credits - show error to player
                LOGGER.warn("User {} is out of AI credits (same account after reauth)", player.getName().getString());
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("Insufficient AI credits. Please top up your account at https://player2.game").withStyle(ChatFormatting.RED));
                }
                throw new Exception("Insufficient AI credits");
            }

            throw e;
        }
    }

    private static Map<String, String> getHeaders(String clientId, String token){
        Map<String, String> headers = new HashMap<>();
        headers.put("player2-game-key", clientId);
        headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    public static String awaitToken(Player player, String clientId) throws ExecutionException, InterruptedException {
        return AuthenticationManager.getInstance().authenticate(player, clientId).get();
    }
}
