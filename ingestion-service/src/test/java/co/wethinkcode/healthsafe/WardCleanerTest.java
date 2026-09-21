package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardCleanerTest {

    private final WardCleaner cleaner = new WardCleaner();

    private static String[] row(String... cells) {
        return cells;
    }

    private WardRecord cleanOne(String... cells) {
        var result = cleaner.clean(Collections.singletonList(cells));
        assertEquals(1, result.wards().size());
        return result.wards().get(0);
    }

    private static boolean hasNote(WardRecord ward, String text) {
        return ward.notes().stream().anyMatch(note -> note.contains(text));
    }

    @Test
    void fixesCasingAndPaddingInIdWingAndDepartment() {
        WardRecord ward = cleanOne(" w-02 ", "  west   wing ", "PAEDIATRICS", "4");

        assertEquals("W-02", ward.wardId());
        assertEquals("West Wing", ward.wing());
        assertEquals("Paediatrics", ward.department());
        assertEquals(4, ward.bedsAvailable());
        assertTrue(ward.notes().isEmpty());
    }

    @Test
    void collapsesDoubleSpacesInsideAField() {
        assertEquals("South Wing", cleanOne("W-10", "South  Wing", "Maternity", "1").wing());
    }

    @Test
    void acceptsIdsWithoutTheDashOrLeadingZero() {
        assertEquals("W-05", cleanOne("w05", "East Wing", "ICU", "1").wardId());
        assertEquals("W-05", cleanOne("W-5", "East Wing", "ICU", "1").wardId());
    }

    @Test
    void mapsRegionalSpellingToOneName() {
        WardRecord ward = cleanOne("W-11", "East Wing", "Pediatrics", "3");

        assertEquals("Paediatrics", ward.department());
        assertTrue(hasNote(ward, "normalised to 'Paediatrics'"));
    }

    @Test
    void keepsIcuAsAnAcronym() {
        assertEquals("ICU", cleanOne("W-09", "North Wing", "icu", "2").department());
    }

    @Test
    void keepsUnknownDepartmentsInTitleCase() {
        assertEquals("Burns Unit", cleanOne("W-20", "East Wing", "BURNS  unit", "2").department());
    }

    @ParameterizedTest
    @ValueSource(strings = {"N/A", "n/a", "TBD", "unknown", "-", "NaN", "", "   "})
    void placeholderBedCountsBecomeNullWithANote(String placeholder) {
        WardRecord ward = cleanOne("W-02", "West Wing", "Cardiology", placeholder);

        assertNull(ward.bedsAvailable());
        assertTrue(hasNote(ward, "bedsAvailable was"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"five", "full", "3.5"})
    void nonNumericBedCountsBecomeNullWithANote(String value) {
        WardRecord ward = cleanOne("W-05", "East Wing", "Paediatrics", value);

        assertNull(ward.bedsAvailable());
        assertTrue(hasNote(ward, "non-numeric ('" + value + "')"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-2"})
    void negativeBedCountsBecomeNullWithANote(String value) {
        WardRecord ward = cleanOne("W-04", "North Wing", "Oncology", value);

        assertNull(ward.bedsAvailable());
        assertTrue(hasNote(ward, "negative"));
    }

    @Test
    void unrealisticBedCountsBecomeNullWithANote() {
        WardRecord ward = cleanOne("W-13", "North Wing", "Oncology", "2023");

        assertNull(ward.bedsAvailable());
        assertTrue(hasNote(ward, "unrealistic"));
    }

    @Test
    void zeroBedsIsARealValueNotMissing() {
        WardRecord ward = cleanOne("W-03", "East Wing", "Cardiology", "0");

        assertEquals(0, ward.bedsAvailable());
        assertTrue(ward.notes().isEmpty());
    }

    @Test
    void missingWingBecomesNullWithANote() {
        WardRecord ward = cleanOne("W-08", "", "Oncology", "4");

        assertNull(ward.wing());
        assertTrue(hasNote(ward, "wing was blank"));
    }

    @Test
    void shortRowsDoNotCrash() {
        WardRecord ward = cleanOne("W-01");

        assertEquals("W-01", ward.wardId());
        assertNull(ward.wing());
        assertNull(ward.department());
        assertNull(ward.bedsAvailable());
    }

    @Test
    void nullCellsDoNotCrash() {
        WardRecord ward = cleanOne("W-01", null, null, null);

        assertNull(ward.wing());
        assertNull(ward.bedsAvailable());
    }

    @Test
    void duplicateRowsMergeIntoOneAndKeepTheUsableValue() {
        var result = cleaner.clean(List.of(
                row("W-05", "East Wing", "Paediatrics", "5"),
                row("w-05", "east wing ", "PAEDIATRICS", "five")));

        assertEquals(1, result.wards().size());
        WardRecord ward = result.wards().get(0);
        assertEquals("W-05", ward.wardId());
        assertEquals(5, ward.bedsAvailable());
        assertTrue(hasNote(ward, "merged duplicate row"));
        assertTrue(hasNote(ward, "non-numeric ('five')"));
    }

    @Test
    void duplicateFillsAGapLeftByTheFirstRow() {
        var result = cleaner.clean(List.of(
                row("W-07", "West Wing", "Cardiology", "TBD"),
                row("w-07", "west wing", "cardiology", "4")));

        assertEquals(4, result.wards().get(0).bedsAvailable());
    }

    @Test
    void duplicateConflictKeepsTheFirstValueAndSaysSo() {
        var result = cleaner.clean(List.of(
                row("W-01", "East Wing", "Cardiology", "3"),
                row("w-01", "West Wing", "Cardiology", "4")));

        WardRecord ward = result.wards().get(0);
        assertEquals("East Wing", ward.wing());
        assertEquals(3, ward.bedsAvailable());
        assertTrue(hasNote(ward, "wing conflict: kept 'East Wing', ignored 'West Wing'"));
        assertTrue(hasNote(ward, "bedsAvailable conflict: kept '3', ignored '4'"));
    }

    @Test
    void oneBadRowIsRejectedAndTheRestStillClean() {
        var result = cleaner.clean(List.of(
                row("W-01", "East Wing", "Cardiology", "3"),
                row("???", "East Wing", "Cardiology", "3"),
                row("", "East Wing", "Cardiology", "3"),
                row("W-02", "West Wing", "Oncology", "1")));

        assertEquals(2, result.wards().size());
        assertEquals(2, result.rejectedRows().size());
        assertTrue(result.rejectedRows().get(0).startsWith("row 2:"));
        assertTrue(result.rejectedRows().get(1).startsWith("row 3:"));
    }

    @Test
    void keepsTheOrderWardsFirstAppeared() {
        var result = cleaner.clean(List.of(
                row("W-03", "East Wing", "ICU", "1"),
                row("W-01", "East Wing", "ICU", "1"),
                row("W-02", "East Wing", "ICU", "1")));

        assertEquals(List.of("W-03", "W-01", "W-02"),
                result.wards().stream().map(WardRecord::wardId).toList());
    }
}
