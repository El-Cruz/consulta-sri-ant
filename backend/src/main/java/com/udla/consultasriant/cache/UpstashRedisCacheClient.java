package com.udla.consultasriant.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente REST hacia Upstash Redis Cloud Cache con respaldo local en memoria.
 */
@Component
public class UpstashRedisCacheClient implements CacheStore {

    private final String restUrl;
    private final String restToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> localMirror = new ConcurrentHashMap<>();

    public UpstashRedisCacheClient(
            @Value("${app.cache.upstash-rest-url:}") String configuredUrl,
            @Value("${app.cache.upstash-rest-token:}") String configuredToken
    ) {
        Map<String, String> envFile = loadDotEnv();
        this.restUrl = firstNonBlank(configuredUrl, System.getenv("UPSTASH_REDIS_REST_URL"), envFile.get("UPSTASH_REDIS_REST_URL"));
        this.restToken = firstNonBlank(configuredToken, System.getenv("UPSTASH_REDIS_REST_TOKEN"), envFile.get("UPSTASH_REDIS_REST_TOKEN"));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    public Optional<String> get(String key) {
        if (hasCredentials()) {
            try {
                JsonNode node = executeCommand(List.of("GET", key));
                if (node != null && node.has("result") && !node.get("result").isNull()) {
                    String value = node.get("result").asString();
                    localMirror.put(key, value);
                    return Optional.of(value);
                }
                if (node != null && node.has("result") && node.get("result").isNull()) {
                    return Optional.empty();
                }
            } catch (Exception ignored) {
                // Si la red hacia Upstash falla momentáneamente, intenta con espejo local.
            }
        }
        return Optional.ofNullable(localMirror.get(key));
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        localMirror.put(key, value);
        if (!hasCredentials()) {
            return;
        }
        try {
            executeCommand(List.of("SET", key, value, "EX", String.valueOf(ttlSeconds)));
        } catch (Exception ignored) {
            // Mantiene el espejo local si Upstash no responde temporalmente.
        }
    }

    @Override
    public boolean isCloudConnected() {
        if (!hasCredentials()) {
            return false;
        }
        try {
            JsonNode node = executeCommand(List.of("PING"));
            return node != null && node.has("result") && "PONG".equalsIgnoreCase(node.get("result").asString());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return hasCredentials() ? "Upstash Redis Cloud" : "Memoria Local (Sin credenciales Upstash)";
    }

    private JsonNode executeCommand(List<String> command) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(command);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(restUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + restToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Upstash REST status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private boolean hasCredentials() {
        return restUrl != null && !restUrl.isBlank() && restToken != null && !restToken.isBlank();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new ConcurrentHashMap<>();
        for (Path path : List.of(Path.of(".env"), Path.of("../.env"))) {
            if (Files.isRegularFile(path)) {
                try {
                    for (String line : Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                            continue;
                        }
                        int idx = trimmed.indexOf('=');
                        String k = trimmed.substring(0, idx).trim();
                        String v = trimmed.substring(idx + 1).trim();
                        values.putIfAbsent(k, v);
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return values;
    }
}
