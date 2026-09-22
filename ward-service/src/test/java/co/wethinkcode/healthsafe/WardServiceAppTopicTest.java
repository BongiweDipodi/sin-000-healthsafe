package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WardServiceAppTopicTest {

    @Test
    void parsesStaffingUpdateJsonIntoAMap() {
        Map<String, Object> update = WardServiceApp.parseStaffingUpdate("{\"wardId\":\"W-01\",\"level\":5,\"doctor\":\"Dr. Patel\"}");

        assertEquals("W-01", update.get("wardId"));
        assertEquals(5, ((Number) update.get("level")).intValue());
        assertEquals("Dr. Patel", update.get("doctor"));
    }
}
