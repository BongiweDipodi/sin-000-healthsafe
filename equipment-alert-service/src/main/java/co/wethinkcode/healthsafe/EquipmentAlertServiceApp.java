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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class EquipmentAlertServiceApp {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<Map<String, Object>> ALERT_LOG = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        startQueueConsumer();
        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alerts", ctx -> ctx.json(ALERT_LOG));
    }

    public static void startQueueConsumer() {
        try {
            var factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            var queue = session.createQueue(MqConfig.QUEUE);
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        Map<String, Object> alert = parseAlert(textMessage.getText());
                        if (!alert.isEmpty()) {
                            ALERT_LOG.add(alert);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore transient queue-read issues until the broker is running.
                }
            });
        } catch (Exception ignored) {
            // Ignore transient broker startup race conditions until docker compose is started.
        }
    }

    static Map<String, Object> parseAlert(String body) {
        try {
            return OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
