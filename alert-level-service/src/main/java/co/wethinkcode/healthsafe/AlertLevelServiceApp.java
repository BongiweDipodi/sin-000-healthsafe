package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelServiceApp {

    private static final AtomicInteger EMERGENCY_LEVEL = new AtomicInteger(0);

    public static int getLevel() {
        return EMERGENCY_LEVEL.get();
    }

    public static void setLevel(int level) {
        if (level < 0 || level > 8) {
            throw new IllegalArgumentException("Emergency level must be between 0 and 8");
        }
        EMERGENCY_LEVEL.set(level);
    }

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert-level", ctx -> ctx.json(Map.of("level", getLevel())));
        app.put("/alert-level", ctx -> {
            var payload = ctx.bodyAsClass(Map.class);
            Object rawLevel = payload.get("level");
            int nextLevel = parseLevel(rawLevel);
            setLevel(nextLevel);
            ctx.json(Map.of("level", nextLevel));
        });
        app.get("/alert-level/{level}", ctx -> {
            int requested = parseLevel(ctx.pathParam("level"));
            setLevel(requested);
            ctx.json(Map.of("level", requested));
        });
    }

    private static int parseLevel(Object rawLevel) {
        if (rawLevel instanceof Number number) {
            return validateLevel(number.intValue());
        }
        if (rawLevel instanceof String text) {
            return validateLevel(Integer.parseInt(text.trim()));
        }
        throw new IllegalArgumentException("Emergency level must be between 0 and 8");
    }

    private static int parseLevel(String rawLevel) {
        return validateLevel(Integer.parseInt(rawLevel.trim()));
    }

    private static int validateLevel(int level) {
        if (level < 0 || level > 8) {
            throw new IllegalArgumentException("Emergency level must be between 0 and 8");
        }
        return level;
    }
}
