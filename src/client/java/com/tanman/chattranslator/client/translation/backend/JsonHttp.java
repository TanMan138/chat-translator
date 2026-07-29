package com.tanman.chattranslator.client.translation.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared HTTP helpers for remote translation backends.
 */
public final class JsonHttp {

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private JsonHttp() {
    }

    public static String postJson(String url, String jsonBody, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        HttpResponse<String> response = CLIENT.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpException(response.statusCode(), response.body());
        }
        return response.body();
    }

    public static String postForm(String url, Map<String, String> formFields, Map<String, String> headers)
            throws Exception {
        String body = formFields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        HttpResponse<String> response = CLIENT.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpException(response.statusCode(), response.body());
        }
        return response.body();
    }

    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    public static String parseOllamaResponse(String json) {
        JsonObject root = parseObject(json);
        if (!root.has("response") || root.get("response").isJsonNull()) {
            throw new IllegalArgumentException("Ollama response missing 'response' field");
        }
        return root.get("response").getAsString().trim();
    }

    public static String parseDeepLResponse(String json) {
        JsonObject root = parseObject(json);
        if (!root.has("translations") || root.getAsJsonArray("translations").isEmpty()) {
            throw new IllegalArgumentException("DeepL response missing translations");
        }
        return root.getAsJsonArray("translations")
                .get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    public static String parseGoogleTranslateResponse(String json) {
        JsonObject root = parseObject(json);
        JsonObject data = root.getAsJsonObject("data");
        if (data == null || !data.has("translations") || data.getAsJsonArray("translations").isEmpty()) {
            throw new IllegalArgumentException("Google response missing translations");
        }
        return data.getAsJsonArray("translations")
                .get(0).getAsJsonObject()
                .get("translatedText").getAsString();
    }

    public static String normalizeEndpoint(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class HttpException extends Exception {
        private final int statusCode;

        public HttpException(int statusCode, String body) {
            super("HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
