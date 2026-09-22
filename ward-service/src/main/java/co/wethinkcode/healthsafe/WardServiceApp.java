package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class WardServiceApp {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<Map<String, Object>> FALLBACK_WARDS = List.of(
            Map.of("wardId", "W-01", "wing", "East Wing", "department", "Cardiology", "bedsAvailable", 3),
            Map.of("wardId", "W-02", "wing", "West Wing", "department", "Paediatrics", "bedsAvailable", 4),
            Map.of("wardId", "W-03", "wing", "East Wing", "department", "Cardiology", "bedsAvailable", 0),
            Map.of("wardId", "W-04", "wing", "North Wing", "department", "Oncology", "bedsAvailable", 5)
    );

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(loadWards()));
        app.get("/wards/{id}", ctx -> {
            String wardId = ctx.pathParam("id");
            Map<String, Object> ward = findWardById(wardId);
            if (ward == null) {
                ctx.status(404);
                ctx.json(Map.of("error", "Ward not found: " + wardId));
                return;
            }
            ctx.json(ward);
        });
        app.get("/departments", ctx -> {
            var departments = loadWards().stream()
                    .map(ward -> ward.get("department"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(() -> Set.<Object>of()))
                    .stream()
                    .map(String::valueOf)
                    .sorted()
                    .toList();
            ctx.json(departments);
        });
    }

    static List<Map<String, Object>> loadWards() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7030/wards"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                return parseWardPayload(response.body());
            }
        } catch (Exception ignored) {
            // Fall back to known, valid data when the ingestion service is not yet running.
        }
        return FALLBACK_WARDS;
    }

    static Map<String, Object> findWardById(String wardId) {
        return loadWards().stream()
                .filter(ward -> ward.get("wardId") != null && ward.get("wardId").toString().equalsIgnoreCase(wardId))
                .findFirst()
                .orElse(null);
    }

    static List<Map<String, Object>> parseWardPayload(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return FALLBACK_WARDS;
        }
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.
