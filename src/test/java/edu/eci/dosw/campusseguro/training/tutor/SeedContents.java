package edu.eci.dosw.campusseguro.training.tutor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The contents of the Flyway seed migrations, read from the SQL files, so the search tests use the real texts.
final class SeedContents {

    record Seed(String id, String title, String body, String categoryId) {
    }

    private static final Pattern ROW = Pattern.compile(
            "\\(\\s*'((?:[^']|'')*)',\\s*'((?:[^']|'')*)',\\s*'((?:[^']|'')*)',\\s*'((?:[^']|'')*)'");

    private SeedContents() {
    }

    static List<Seed> read(String migration) {
        try {
            String sql = Files.readString(Path.of("src/main/resources/db/migration", migration), StandardCharsets.UTF_8);
            String inserts = sql.substring(sql.indexOf("INSERT INTO content"));
            int end = inserts.indexOf(';', inserts.lastIndexOf("')"));
            Matcher matcher = ROW.matcher(inserts.substring(0, end < 0 ? inserts.length() : end));
            List<Seed> seeds = new ArrayList<>();
            while (matcher.find()) {
                seeds.add(new Seed(unquote(matcher.group(1)), unquote(matcher.group(2)), unquote(matcher.group(3)),
                        unquote(matcher.group(4))));
            }
            return seeds;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String unquote(String text) {
        return text.replace("''", "'");
    }
}
