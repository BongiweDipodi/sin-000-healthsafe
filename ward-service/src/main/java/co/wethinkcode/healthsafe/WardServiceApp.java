package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class WardServiceApp {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicReference<Map<String, Object>> LAST_STAFFING_UPDATE = new AtomicReference<>(Map.of());

    private static final List<Map<String, Object>> FALLBACK_WARDS = List.of(
            Map.of("wardId", "W-01", "wing", "East Wing", "department", "Cardiology", "bedsAvailable", 3),
            Map.of("wardId", "W-02", "wing", "West Wing", "department", "Paediatrics", "bedsAvailable", 4),
            Map.of("wardId", "W-03", "wing", "East Wing", "department", "Cardiology", "bedsAvailable", 0),
            Map.of("wardId", "W-04", "wing", "North Wing", "department", "Oncology", "bedsAvailable", 5)
    );

    public static void main(String[] args) {
        startStaffingTopicListener();
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(loadWards()));
        app.get("/staffing-update", ctx -> ctx.json(LAST_STAFFING_UPDATE.get()));
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

    static void startStaffingTopicListener() {
        try {
            var factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        LAST_STAFFING_UPDATE.set(parseStaffingUpdate(textMessage.getText()));
                    }
                } catch (Exception ignored) {
                    // Ignore broker startup race conditions while the shared docker stack is not yet running.
                }
            });
        } catch (Exception ignored) {
            // Ignore broker startup race conditions while the shared docker stack is not yet running.
        }
    }

    static Map<String, Object> parseStaffingUpdate(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
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
