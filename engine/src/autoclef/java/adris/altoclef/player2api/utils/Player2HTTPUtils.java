/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package adris.altoclef.player2api.utils;

import adris.altoclef.player2api.LlmConfig;
import adris.altoclef.player2api.auth.AuthKey;
import adris.altoclef.player2api.auth.AuthenticationManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Player2HTTPUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String WEB_API_URL = "https://api.player2.game";

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
            if (e.getStatusCode() == 401) {
                LOGGER.warn("Received 401 Unauthorized for {}. Invalidating token.", new AuthKey(player.getUUID(), clientId));
                AuthenticationManager.getInstance().invalidateToken(player, clientId);
                throw new Exception("Token expired, re-authentication started.", e);
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
