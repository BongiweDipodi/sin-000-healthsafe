package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffingServiceAppTest {

    @Test
    void returnsTheCorrectDoctorForLowRiskLevel() {
        assertEquals("Dr. Alvarez", StaffingServiceApp.computeOnCallDoctor("W-01", 2));
    }

    @Test
    void returnsTheCorrectDoctorForModerateRiskLevel() {
        assertEquals("Dr. Patel", StaffingServiceApp.computeOnCallDoctor("W-01", 5));
    }

    @Test
    void returnsTheCorrectDoctorForCriticalLevel() {
        assertEquals("Dr. Chen", StaffingServiceApp.computeOnCallDoctor("W-01", 8));
    }

    @Test
    void rejectsEmergencyLevelsOutsideTheAllowedRange() {
        assertThrows(IllegalArgumentException.class, () -> StaffingServiceApp.computeOnCallDoctor("W-01", -1));
        assertThrows(IllegalArgumentException.class, () -> StaffingServiceApp.computeOnCallDoctor("W-01", 9));
    }
}
