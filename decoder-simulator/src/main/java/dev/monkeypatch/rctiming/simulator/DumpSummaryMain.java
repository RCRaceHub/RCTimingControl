package dev.monkeypatch.rctiming.simulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for {@code RCTimingControl dump-summary}: reports what a recorded {@code .dump} file holds.
 *
 * <pre>
 * RCTimingControl dump-summary --file=capture.dump [--split-gap=120] [--windows=races.csv]
 * </pre>
 *
 * <p>{@code --split-gap} starts a new section after that many seconds with no passings. {@code --windows}
 * names a text file with one race per line, {@code name,start,end}, the instants in ISO form such as
 * {@code 2026-11-07T10:15:00Z} (UTC), taken from the races' start and finish times in RCTimingControl; passings
 * are counted against the window they fall in. Both need the {@code .times} file the recorder writes beside
 * the dump. Exits with 0 when the report is printed, 2 for bad flags or a missing file, and 1 when a file
 * cannot be read. Give one of {@code --split-gap} and {@code --windows}, not both. A window includes both its
 * start and its end instants, so cars that cross the line after a race has finished fall outside it unless the
 * window runs on past the finish. See {@link DumpSummary}.
 */
public class DumpSummaryMain {

    private DumpSummaryMain() {
    }

    /** Parses the flags and prints the report. */
    public static void main(String[] args) {
        Path file = null;
        Path windowsFile = null;
        long splitGap = 0;
        try {
            for (String arg : args) {
                if (arg.startsWith("--file=")) {
                    file = Path.of(arg.substring("--file=".length()));
                } else if (arg.startsWith("--windows=")) {
                    windowsFile = Path.of(arg.substring("--windows=".length()));
                } else if (arg.startsWith("--split-gap=")) {
                    splitGap = Long.parseLong(arg.substring("--split-gap=".length()));
                    if (splitGap < 0) {
                        usage("--split-gap must be 0 or more seconds");
                    }
                } else {
                    usage("Unknown argument: " + arg);
                }
            }
        } catch (InvalidPathException e) {
            usage("A file name is not valid");
        } catch (NumberFormatException e) {
            usage("--split-gap must be a whole number of seconds");
        }
        if (file == null) {
            usage("--file is required");
        }
        if (!Files.isRegularFile(file)) {
            usage("No such file: " + file);
        }
        if (windowsFile != null && splitGap > 0) {
            usage("Use --split-gap or --windows, not both");
        }
        if (windowsFile != null && !Files.isRegularFile(windowsFile)) {
            usage("No such file: " + windowsFile);
        }
        try {
            List<DumpSummary.Window> windows = windowsFile == null ? List.of() : readWindows(windowsFile);
            DumpSummary.read(file, splitGap, windows).print(System.out);
        } catch (IOException e) {
            System.err.println("Error: could not read " + file + " or its .times file or the windows file: " + e);
            System.exit(1);
        }
    }

    private static List<DumpSummary.Window> readWindows(Path file) throws IOException {
        List<DumpSummary.Window> windows = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            // The name may hold commas: the last two fields are the instants
            int end = line.lastIndexOf(',');
            int start = end < 0 ? -1 : line.lastIndexOf(',', end - 1);
            try {
                Instant from = Instant.parse(line.substring(start + 1, end).trim());
                Instant to = Instant.parse(line.substring(end + 1).trim());
                if (start < 1 || to.isBefore(from)) {
                    throw new IllegalArgumentException(line);
                }
                windows.add(new DumpSummary.Window(line.substring(0, start).trim(), from, to));
            } catch (DateTimeParseException | IllegalArgumentException | IndexOutOfBoundsException e) {
                usage("Not a race window (name,start,end in UTC): " + line);
            }
        }
        return windows;
    }

    private static void usage(String error) {
        System.err.println("Error: " + error);
        System.err.println("""
            Usage:
              dump-summary --file=capture.dump [--split-gap=120] [--windows=races.csv]
            """);
        System.exit(2);
    }
}
