package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WardServiceAppTest {

    @Test
    void parseWardPayloadParsesJsonList() {
        List<Map<String, Object>> wards = WardServiceApp.parseWardPayload("[\n" +
                "  {\"wardId\":\"W-01\",\"wing\":\"East Wing\",\"department\":\"Cardiology\",\"bedsAvailable\":3}\n" +
                "]");

        assertEquals(1, wards.size());
        assertEquals("W-01", wards.get(0).get("wardId"));
        assertEquals("Cardiology", wards.get(0).get("department"));
    }

    @Test
    void findWardByIdMatchesCaseInsensitiveId() {
        Map<String, Object> ward = WardServiceApp.findWardById("w-02");

        assertNotNull(ward);
        assertEquals("W-02", ward.get("wardId"));
    }

    @Test
    void findWardByIdReturnsNullForUnknownWard() {
        assertNull(WardServiceApp.findWardById("W-99"));
    }
}
