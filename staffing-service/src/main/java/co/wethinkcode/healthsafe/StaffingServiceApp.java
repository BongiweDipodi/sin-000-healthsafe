package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class StaffingServiceApp {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/schedule", ctx -> {
            String wardId = ctx.queryParam("wardId");
            String rawLevel = ctx.queryParam("level");
            int level = rawLevel == null ? 0 : Integer.parseInt(rawLevel);
            ctx.json(Map.of(
                    "wardId", wardId,
                    "level", level,
                    "doctor", computeOnCallDoctor(wardId, level)
            ));
        });
        app.get("/schedule/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId");
            int level = fetchAlertLevel();
            validateWard(wardId);
            ctx.json(Map.of(
                    "wardId", wardId,
                    "level", level,
                    "doctor", computeOnCallDoctor(wardId, level)
            ));
        });
    }

    public static String computeOnCallDoctor(String wardId, int emergencyLevel) {
        if (emergencyLevel < 0 || emergencyLevel > 8) {
            throw new IllegalArgumentException("Emergency level must be between 0 and 8");
        }

        List<String> doctors = List.of(
                "Dr. Alvarez",
                "Dr. Patel",
                "Dr. Chen",
                "Dr. Singh"
        );

        int index = switch (emergencyLevel) {
            case 0, 1, 2 -> 0;
            case 3, 4, 5 -> 1;
            case 6, 7, 8 -> 2;
            default -> throw new IllegalArgumentException("Emergency level must be between 0 and 8");
        };

        return doctors.get(Math.min(index, doctors.size() - 1));
    }

    static void validateWard(String wardId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7031/wards/" + wardId))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new IllegalArgumentException("Ward not found: " + wardId);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ignored) {
            if (wardId == null || wardId.isBlank()) {
                throw new IllegalArgumentException("Ward id is required");
            }
        }
    }

    static int fetchAlertLevel() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7032/alert-level"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                String body = response.body();
                int start = body.indexOf("\"level\"");
                if (start >= 0) {
                    int colon = body.indexOf(':', start);
                    if (colon >= 0) {
                        String number = body.substring(colon + 1).replaceAll("[^0-9-]", "").trim();
                        if (!number.isEmpty()) {
                            return Integer.parseInt(number);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to a safe default if the alert-level service is not running yet.
        }
        return 0;
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
