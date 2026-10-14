package dev.monkeypatch.rctiming.practice;

import dev.monkeypatch.rctiming.practice.dto.PracticeDayRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Totals each driver's laps across the day's practice sessions (#151). Pure, so it is tested without a database.
 *
 * <p>A driver is a competitor when the transponder was named, else the transponder itself. The best N laps are taken
 * within one run, where a run is one session's stretch of laps for one transponder, as in the live table: a pause
 * between runs is never part of the average.
 */
final class PracticeDayAggregator {

    /** A saved lap: where it was set and its time. Laps must arrive in the order they were crossed. */
    record Lap(long sessionId, String transponder, int runNumber, long lapTimeMs) {}

    /** Who a transponder was in a session: the competitor and name when known. */
    record Driver(Long competitorId, String name) {}

    private PracticeDayAggregator() {}

    static List<PracticeDayRowDto> aggregate(List<Lap> laps, DriverLookup drivers, int n) {
        Map<String, Totals> byDriver = new LinkedHashMap<>();
        for (Lap lap : laps) {
            Driver who = drivers.find(lap.sessionId(), lap.transponder());
            String key = who.competitorId() != null ? "c" + who.competitorId() : "t" + lap.transponder();
            byDriver.computeIfAbsent(key, k -> new Totals(who)).add(lap);
        }
        List<PracticeDayRowDto> rows = new ArrayList<>();
        byDriver.values().forEach(t -> rows.add(t.toRow(n)));
        rows.sort(Comparator
                .comparing((PracticeDayRowDto r) -> r.bestConsecutiveNMs() == null ? 1 : 0)
                .thenComparing(r -> r.bestConsecutiveNMs() == null ? 0L : r.bestConsecutiveNMs())
                .thenComparing(r -> r.bestLapMs() == null ? Long.MAX_VALUE : r.bestLapMs())
                .thenComparing(r -> r.name() == null ? "" : r.name()));
        return rows;
    }

    @FunctionalInterface
    interface DriverLookup {
        Driver find(long sessionId, String transponder);
    }

    private static final class Totals {
        private final Driver driver;
        private final Set<Long> sessions = new LinkedHashSet<>();
        private final Set<String> transponders = new TreeSet<>();
        /** The lap times of each run, keyed by session, transponder and run. */
        private final Map<String, List<Long>> runs = new LinkedHashMap<>();

        Totals(Driver driver) {
            this.driver = driver;
        }

        void add(Lap lap) {
            sessions.add(lap.sessionId());
            transponders.add(lap.transponder());
            runs.computeIfAbsent(lap.sessionId() + "/" + lap.transponder() + "/" + lap.runNumber(),
                    k -> new ArrayList<>()).add(lap.lapTimeMs());
        }

        PracticeDayRowDto toRow(int n) {
            int laps = 0;
            Long bestLap = null;
            Long bestN = null;
            for (List<Long> times : runs.values()) {
                laps += times.size();
                for (long t : times) {
                    bestLap = bestLap == null ? t : Math.min(bestLap, t);
                }
                Long window = bestWindow(times, n);
                if (window != null) {
                    bestN = bestN == null ? window : Math.min(bestN, window);
                }
            }
            return new PracticeDayRowDto(driver.competitorId(), driver.name(), String.join(", ", transponders),
                    sessions.size(), runs.size(), laps, bestLap, bestN);
        }

        private static Long bestWindow(List<Long> times, int n) {
            if (n < 1 || times.size() < n) {
                return null;
            }
            long sum = 0;
            for (int i = 0; i < n; i++) {
                sum += times.get(i);
            }
            long best = sum;
            for (int i = n; i < times.size(); i++) {
                sum += times.get(i) - times.get(i - n);
                best = Math.min(best, sum);
            }
            return best;
        }
    }
}
