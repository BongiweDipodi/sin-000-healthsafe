package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class IngestionServiceApp {

    public static void main(String[] args) {
        WardCleaner.CleaningResult cleaningResult = loadWards();
        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        
        // TODO: read and clean src/main/resources/wards-outdated.csv (wards, wings, specialist departments data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
        
        app.get("/wards", ctx -> ctx.json(cleaningResult.wards()));
        app.get("/wards/rejected", ctx -> ctx.json(cleaningResult.rejectedRows()));
    }

    private static WardCleaner.CleaningResult loadWards() {
        var rows = new ArrayList<String[]>();
        try (var input = IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv")) {
            if (input == null) {
                throw new IllegalStateException("wards-outdated.csv was not found on the classpath");
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                 CSVReader csv = new CSVReader(reader)) {
                csv.readNext();
                String[] row;
                while ((row = csv.readNext()) != null) {
                    rows.add(row);
                }
            }
        } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
            throw new IllegalStateException("Could not read wards-outdated.csv", e);
        }
        return new WardCleaner().clean(List.copyOf(rows));
    }
}
