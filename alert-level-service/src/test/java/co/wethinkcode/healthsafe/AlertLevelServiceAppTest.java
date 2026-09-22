package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertLevelServiceAppTest {

    @Test
    void defaultsToZero() {
        AlertLevelServiceApp.setLevel(0);
        assertEquals(0, AlertLevelServiceApp.getLevel());
    }

    @Test
    void acceptsValuesWithinRange() {
        AlertLevelServiceApp.setLevel(8);
        assertEquals(8, AlertLevelServiceApp.getLevel());
    }

    @Test
    void rejectsValuesOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.setLevel(-1));
        assertThrows(IllegalArgumentException.class, () -> AlertLevelServiceApp.setLevel(9));
    }
}
