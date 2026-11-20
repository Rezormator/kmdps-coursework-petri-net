package ua.kpi.workshop.experiments;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Output implements AutoCloseable {

    private static final Path RESULTS = Path.of("results");

    private final PrintWriter writer;

    public Output(String fileName) {
        try {
            Files.createDirectories(RESULTS);
            writer = new PrintWriter(Files.newBufferedWriter(RESULTS.resolve(fileName), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path resultsDirectory() {
        return RESULTS.toAbsolutePath();
    }

    public static Path pathFor(String fileName) {
        try {
            Files.createDirectories(RESULTS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return RESULTS.resolve(fileName);
    }

    public void line(String text) {
        System.out.println(text);
        writer.println(text);
    }

    public void line() {
        line("");
    }

    public void table(String[] header, List<String[]> rows) {
        List<String[]> all = new ArrayList<>();
        all.add(header);
        all.addAll(rows);
        int[] widths = new int[header.length];
        for (String[] row : all) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        for (int r = 0; r < all.size(); r++) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < header.length; i++) {
                if (i > 0) {
                    line.append(" | ");
                }
                String cell = all.get(r)[i];
                line.append(cell).append(" ".repeat(widths[i] - cell.length()));
            }
            line(line.toString());
            if (r == 0) {
                StringBuilder separator = new StringBuilder();
                for (int i = 0; i < widths.length; i++) {
                    if (i > 0) {
                        separator.append("-+-");
                    }
                    separator.append("-".repeat(widths[i]));
                }
                line(separator.toString());
            }
        }
    }

    public static String f(double value, int digits) {
        return String.format(java.util.Locale.ROOT, "%." + digits + "f", value);
    }

    @Override
    public void close() {
        writer.close();
    }
}
