package dev.monkeypatch.rctiming.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a {@code .dump} file made by {@link DecoderRecorder} and reports what is in it, so a test run can be
 * checked against what RCTimingControl credited: passings per transponder, the gaps in the decoder's
 * sequence numbers, and the shortest and longest time between a transponder's passings.
 *
 * <p>The decoder numbers STATUS and PASSING records from one counter, so a missing number means a record was
 * lost between the decoder and the file. Passing times are the decoder's own clock (seconds since power-on).
 *
 * <p>The dump does not say which race was running. The {@code .times} sidecar next to it holds the laptop's
 * clock (UTC milliseconds) for each record, so when it exists it is read instead and the passings can be
 * split into sections: by a quiet spell with no passings ({@code splitGapSeconds}), or by race windows
 * (start and end instants copied from the races' times in RCTimingControl). Run by {@link DumpSummaryMain}
 * on the command line's thread; it reads the file once and touches nothing else.
 */
final class DumpSummary {

    private static final int SOH = 0x01;

    /** A race window as the caller names it: the section name and the instants it starts and ends. */
    record Window(String name, Instant start, Instant end) {
    }

    /** A named stretch of the recording with its own per-transponder counts. */
    static final class Section {
        final String name;
        final Instant start;
        final Instant end;
        final Map<String, Transponder> transponders = new TreeMap<>();
        long passings;
        long firstMs = -1;
        long lastMs = -1;

        Section(String name, Instant start, Instant end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }

        void forgetLastPassings() {
            transponders.values().forEach(t -> t.last = Double.NaN);
        }

        void add(String id, double time, long wallMs) {
            transponders.computeIfAbsent(id, k -> new Transponder()).add(time);
            passings++;
            if (wallMs >= 0) {
                if (firstMs < 0) {
                    firstMs = wallMs;
                }
                lastMs = wallMs;
            }
        }
    }

    /** What was seen for one transponder. */
    static final class Transponder {
        long passings;
        double last = Double.NaN;
        double shortest = Double.NaN;
        double longest = Double.NaN;

        void add(double time) {
            if (!Double.isNaN(last)) {
                double gap = time - last;
                shortest = Double.isNaN(shortest) ? gap : Math.min(shortest, gap);
                longest = Double.isNaN(longest) ? gap : Math.max(longest, gap);
            }
            last = time;
            passings++;
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private final List<Section> windows;
    private final long splitGapMs;
    private final List<String> gaps = new ArrayList<>();
    private long statusRecords;
    private long passings;
    private long unreadable;
    private long previousSeq = -1;
    private long previousPassingMs = -1;
    private boolean hasWallClock;
    private Path source;

    private DumpSummary(long splitGapMs, List<Section> windows) {
        this.splitGapMs = splitGapMs;
        this.windows = windows;
    }

    /**
     * Reads the records of {@code file}, or of its {@code .times} sidecar when that exists.
     *
     * @param splitGapSeconds a quiet spell this long between passings starts a new section; 0 for no split
     * @param windows race windows to split by instead; empty for none
     */
    static DumpSummary read(Path file, long splitGapSeconds, List<Window> windows) throws IOException {
        List<Section> ws = new ArrayList<>();
        windows.forEach(w -> ws.add(new Section(w.name(), w.start(), w.end())));
        DumpSummary summary = new DumpSummary(splitGapSeconds * 1000, ws);
        Path sidecar = file.resolveSibling(file.getFileName() + ".times");
        boolean useSidecar = Files.isRegularFile(sidecar);
        try (BufferedReader in = Files.newBufferedReader(useSidecar ? sidecar : file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = in.readLine()) != null) {
                summary.add(line, useSidecar);
            }
        }
        summary.hasWallClock = useSidecar;
        summary.source = useSidecar ? sidecar : file;
        return summary;
    }

    /** Reads {@code file} with no splitting. */
    static DumpSummary read(Path file) throws IOException {
        return read(file, 0, List.of());
    }

    private void add(String raw, boolean timed) {
        String line = raw.replace("\r", "");
        if (line.indexOf(SOH) >= 0) {
            line = line.substring(line.indexOf(SOH) + 1);
        }
        if (line.isBlank()) {
            return;
        }
        String[] f = line.split("\t");
        long wallMs = -1;
        try {
            if (timed) {
                wallMs = Long.parseLong(f[0]);
                f = Arrays.copyOfRange(f, 1, f.length);
            }
            boolean passing = f[0].equals("@");
            if (!passing && !f[0].equals("#")) {
                unreadable++;
                return;
            }
            // A record with a good sequence number but a bad time still moves the sequence on, so a
            // gap isn't reported for it; it is counted as unreadable, not as a passing
            long seq = Long.parseLong(f[2]);
            if (previousSeq >= 0 && seq != previousSeq + 1) {
                if (seq > previousSeq) {
                    gaps.add((seq - previousSeq - 1) + " missing after " + previousSeq);
                } else if (seq == previousSeq) {
                    gaps.add("record " + seq + " repeated");
                } else {
                    gaps.add("sequence went back from " + previousSeq + " to " + seq
                            + " (decoder restarted or files joined)");
                    // The decoder's clock restarted too, so times before this point can't be compared
                    sections.forEach(Section::forgetLastPassings);
                    windows.forEach(Section::forgetLastPassings);
                }
            }
            previousSeq = seq;
            if (!passing) {
                statusRecords++;
                return;
            }
            double time = Double.parseDouble(f[4]);
            if (!Double.isFinite(time)) {
                unreadable++;
                return;
            }
            Section section = sectionFor(wallMs);
            passings++;
            section.add(f[3], time, wallMs);
            previousPassingMs = wallMs;
        } catch (RuntimeException e) {
            unreadable++;
        }
    }

    private Section sectionFor(long wallMs) {
        if (!windows.isEmpty() && wallMs >= 0) {
            Instant at = Instant.ofEpochMilli(wallMs);
            for (Section w : windows) {
                if (!at.isBefore(w.start) && !at.isAfter(w.end)) {
                    return w;
                }
            }
            return named("Outside every race window");
        }
        boolean split = splitGapMs > 0 && wallMs >= 0 && previousPassingMs >= 0
                && wallMs - previousPassingMs > splitGapMs;
        if (sections.isEmpty() || split) {
            sections.add(new Section(splitGapMs > 0 ? "Run " + (sections.size() + 1) : "Whole recording", null, null));
        }
        return sections.get(sections.size() - 1);
    }

    private Section named(String name) {
        return sections.stream().filter(s -> s.name.equals(name)).findFirst().orElseGet(() -> {
            Section s = new Section(name, null, null);
            sections.add(s);
            return s;
        });
    }

    /** Number of PASSING records in the file. */
    long passings() {
        return passings;
    }

    /** The sequence-number gaps found, oldest first. */
    List<String> gaps() {
        return gaps;
    }

    /** Passings seen for one transponder over the whole recording, 0 if none. */
    long passings(String transponder) {
        return allSections().stream().map(s -> s.transponders.get(transponder))
                .filter(t -> t != null).mapToLong(t -> t.passings).sum();
    }

    /** The sections the passings fell into: windows first (even empty ones), then runs. */
    List<Section> allSections() {
        List<Section> all = new ArrayList<>(windows);
        all.addAll(sections);
        return all;
    }

    /** Prints the report to the stream. */
    void print(PrintStream out) {
        out.println("Read from " + source);
        out.printf("Records: %d passings, %d status, %d unreadable%n", passings, statusRecords, unreadable);
        if (passings == 0) {
            out.println("No passings found.");
        }
        out.println(gaps.isEmpty() ? "Sequence: no gaps" : "Sequence: " + gaps.size() + " gap(s)");
        gaps.forEach(g -> out.println("  " + g));
        if (!hasWallClock && (splitGapMs > 0 || !windows.isEmpty())) {
            out.println("No .times file next to the dump, so it cannot be split; showing the whole recording.");
        }
        for (Section s : allSections()) {
            out.println();
            out.print(s.name);
            if (s.firstMs >= 0) {
                out.printf(" (%s to %s UTC)", Instant.ofEpochMilli(s.firstMs), Instant.ofEpochMilli(s.lastMs));
            }
            out.printf(": %d passings%n", s.passings);
            out.printf("%-14s %9s %12s %12s%n", "Transponder", "Passings", "Shortest s", "Longest s");
            s.transponders.forEach((id, t) -> out.printf("%-14s %9d %12s %12s%n", id, t.passings,
                    Double.isNaN(t.shortest) ? "-" : String.format("%.3f", t.shortest),
                    Double.isNaN(t.longest) ? "-" : String.format("%.3f", t.longest)));
        }
    }
}
