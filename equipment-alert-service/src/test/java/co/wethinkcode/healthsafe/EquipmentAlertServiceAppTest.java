package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentAlertServiceAppTest {

    @Test
    void parsesEquipmentFailureJsonIntoAMap() {
        Map<String, Object> alert = EquipmentAlertServiceApp.parseAlert("{\"wardId\":\"W-01\",\"equipment\":\"Ventilator\",\"severity\":\"critical\"}");

        assertEquals("W-01", alert.get("wardId"));
        assertEquals("Ventilator", alert.get("equipment"));
        assertEquals("critical", alert.get("severity"));
    }
}
