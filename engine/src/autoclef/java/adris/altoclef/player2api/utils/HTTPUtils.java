package adris.altoclef.player2api.utils;

import adris.altoclef.player2api.LlmConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class HTTPUtils {


    public static Map<String, JsonElement> sendRequest(String baseUrl, String endpoint, boolean postRequest, JsonObject requestBody,
                                                       @Nullable Map<String, String> extraHeaders)
            throws Exception {
        // Never block the brain forever on a slow/stuck/down endpoint (a stuck call wedges the
        // single-track LLMCompleter). A timeout throws -> the async task resets isProcessing -> recovery.
        return sendRequest(baseUrl, endpoint, postRequest, requestBody, extraHeaders,
                LlmConfig.connectTimeoutMs, LlmConfig.timeoutMs);
    }

    /**
     * As above, with explicit timeouts.
     *
     * <p>The brain's 90s read timeout is right for a generation loop and wrong for everything else.
     * An embedding is one forward pass; waiting a minute and a half for it means holding a
     * conversation turn open long after the server has plainly stopped answering.
     */
    public static Map<String, JsonElement> sendRequest(String baseUrl, String endpoint, boolean postRequest, JsonObject requestBody,
                                                       @Nullable Map<String, String> extraHeaders,
                                                       int connectTimeoutMs, int readTimeoutMs)
            throws Exception {
        URL url = new URI(baseUrl + endpoint).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setRequestMethod(postRequest ? "POST" : "GET");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json; charset=utf-8");
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        if (postRequest && requestBody != null) {
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            } catch (Throwable v) {
            }
        }

        JsonObject jsonResponse = getJsonObject(connection);
        Map<String, JsonElement> responseMap = new HashMap<>();

        for (Entry<String, JsonElement> entry : jsonResponse.entrySet()) {
            responseMap.put(entry.getKey(), entry.getValue());
        }

        return responseMap;
    }

    private static JsonObject getJsonObject(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();

        if (responseCode >= 400) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            throw new HttpApiException("HTTP " + responseCode + ": " + connection.getResponseMessage() + " Body: " + errorResponse, responseCode);
        }

        if (responseCode != 200) {
            throw new IOException("HTTP " + responseCode + ": " + connection.getResponseMessage());
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        return JsonParser.parseString(response.toString()).getAsJsonObject();

    }
}