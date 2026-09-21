package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WardCleaner {

    static final int MAX_BEDS_PER_WARD = 100;

    private static final Set<String> PLACEHOLDERS = Set.of("", "n/a", "tbd", "unknown", "-", "nan", "null");

    private static final Pattern WARD_ID = Pattern.compile("W-?(\\d+)");

    private static final Map<String, String> DEPARTMENTS = Map.of(
            "cardiology", "Cardiology",
            "paediatrics", "Paediatrics",
            "pediatrics", "Paediatrics",
            "oncology", "Oncology",
            "radiology", "Radiology",
            "maternity", "Maternity",
            "icu", "ICU");

    public record CleaningResult(List<WardRecord> wards, List<String> rejectedRows) {
    }

    public CleaningResult clean(List<String[]> rows) {
        Map<String, WardRecord> wardsById = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            try {
                WardRecord record = cleanRow(rows.get(i));
                wardsById.merge(record.wardId(), record, WardCleaner::merge);
            } catch (RuntimeException e) {
                rejected.add("row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return new CleaningResult(List.copyOf(wardsById.values()), List.copyOf(rejected));
    }

    private WardRecord cleanRow(String[] row) {
        List<String> notes = new ArrayList<>();
        String wardId = cleanId(cell(row, 0));
        String wing = cleanWing(cell(row, 1), notes);
        String department = cleanDepartment(cell(row, 2), notes);
        Integer beds = cleanBeds(cell(row, 3), notes);
        return new WardRecord(wardId, wing, department, beds, notes);
    }

    private static String cleanId(String raw) {
        Matcher matcher = WARD_ID.matcher(normalise(raw).toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid ward id '" + raw.strip() + "'");
        }
        return String.format("W-%02d", Integer.parseInt(matcher.group(1)));
    }

    private static String cleanWing(String raw, List<String> notes) {
        String value = normalise(raw);
        if (isPlaceholder(value)) {
            notes.add(missingNote("wing", value));
            return null;
        }
        return titleCase(value);
    }

    private static String cleanDepartment(String raw, List<String> notes) {
        String value = normalise(raw);
        if (isPlaceholder(value)) {
            notes.add(missingNote("department", value));
            return null;
        }
        String canonical = DEPARTMENTS.get(value.toLowerCase(Locale.ROOT));
        if (canonical == null) {
            return titleCase(value);
        }
        if (!canonical.equalsIgnoreCase(value)) {
            notes.add("department '" + value + "' normalised to '" + canonical + "'");
        }
        return canonical;
    }

    private static Integer cleanBeds(String raw, List<String> notes) {
        String value = normalise(raw);
        if (isPlaceholder(value)) {
            notes.add(missingNote("bedsAvailable", value));
            return null;
        }
        int beds;
        try {
            beds = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            notes.add("bedsAvailable was non-numeric ('" + value + "'), flagged for follow-up");
            return null;
        }
        if (beds < 0) {
            notes.add("bedsAvailable was negative (" + beds + "), flagged for follow-up");
            return null;
        }
        if (beds > MAX_BEDS_PER_WARD) {
            notes.add("bedsAvailable was unrealistic (" + beds + "), flagged for follow-up");
            return null;
        }
        return beds;
    }

    private static WardRecord merge(WardRecord first, WardRecord second) {
        List<String> notes = new ArrayList<>(first.notes());
        notes.add("merged duplicate row for " + first.wardId());
        for (String note : second.notes()) {
            notes.add("duplicate row: " + note);
        }
        return new WardRecord(
                first.wardId(),
                pick("wing", first.wing(), second.wing(), notes),
                pick("department", first.department(), second.department(), notes),
                pick("bedsAvailable", first.bedsAvailable(), second.bedsAvailable(), notes),
                List.copyOf(notes));
    }

    private static <T> T pick(String field, T first, T second, List<String> notes) {
        if (first == null) {
            return second;
        }
        if (second != null && !first.equals(second)) {
            notes.add(field + " conflict: kept '" + first + "', ignored '" + second + "'");
        }
        return first;
    }

    private static String cell(String[] row, int index) {
        return index < row.length && row[index] != null ? row[index] : "";
    }

    /** Trims, and turns any run of whitespace (including non-breaking spaces) into one space. */
    private static String normalise(String raw) {
        return raw.replaceAll("[\\s\\u00A0]+", " ").strip();
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT));
    }

    private static String missingNote(String field, String value) {
        return value.isEmpty()
                ? field + " was blank"
                : field + " was a placeholder ('" + value + "')";
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder();
        for (String word : value.split(" ")) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                  .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}