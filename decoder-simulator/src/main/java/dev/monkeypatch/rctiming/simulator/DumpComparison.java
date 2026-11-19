package dev.monkeypatch.rctiming.simulator;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compares two recordings made at the same time from two decoders (#678): which passings each decoder saw of the
 * same transponder, and how far apart the laptop received them. Run by {@link DumpSummaryMain} when it is given
 * more than one {@code --file}; it only reads the two summaries.
 *
 * <p>RC-4 text carries no absolute time, so RCTimingControl pins each decoder to the laptop's clock when its first
 * passing arrives. The difference in arrival times of one car at the two decoders is therefore what two decoders
 * set to the same loop would disagree by, plus the network's delay. With both decoders on the same loop (or one
 * decoder's loop read through two ports) the true difference is zero, so the median is the offset to correct
 * and the spread says how steady it is. With loops at different places the difference includes the car's travel
 * time between them and is only a check that cars pass both.
 *
 * <p>Passings are paired per transponder in time order: a passing of one decoder pairs with the next passing of
 * the other within {@code windowMs}, so keep the window below the shortest lap. Anything left over is unpaired, which for decoders on the same loop means one
 * decoder missed the car.
 */
final class DumpComparison {

    /** The outcome of comparing decoder {@code b} with decoder {@code a}; differences are b's arrival minus a's. */
    record Result(String a, String b, int paired, int unpairedA, int unpairedB, long medianMs, long minMs, long maxMs) {
    }

    private DumpComparison() {
    }

    /**
     * Pairs the passings of the two recordings and measures how far apart each pair arrived.
     *
     * @param windowMs the most two arrivals of one car may differ by and still count as the same passing
     * @return the result; {@code paired} is 0 and the offsets 0 when nothing paired
     */
    static Result compare(String nameA, DumpSummary a, String nameB, DumpSummary b, long windowMs) {
        List<Long> diffs = new ArrayList<>();
        int unpairedA = 0;
        int unpairedB = 0;
        for (Map.Entry<String, List<Long>> entry : a.wallTimes().entrySet()) {
            List<Long> la = sorted(entry.getValue());
            List<Long> lb = sorted(b.wallTimes().getOrDefault(entry.getKey(), List.of()));
            int i = 0;
            int j = 0;
            while (i < la.size() && j < lb.size()) {
                long diff = lb.get(j) - la.get(i);
                if (Math.abs(diff) <= windowMs) {
                    diffs.add(diff);
                    i++;
                    j++;
                } else if (diff > 0) {
                    unpairedA++;
                    i++;
                } else {
                    unpairedB++;
                    j++;
                }
            }
            unpairedA += la.size() - i;
            unpairedB += lb.size() - j;
        }
        // Transponders only the other decoder saw
        for (Map.Entry<String, List<Long>> entry : b.wallTimes().entrySet()) {
            if (!a.wallTimes().containsKey(entry.getKey())) {
                unpairedB += entry.getValue().size();
            }
        }
        if (diffs.isEmpty()) {
            return new Result(nameA, nameB, 0, unpairedA, unpairedB, 0, 0, 0);
        }
        Collections.sort(diffs);
        int mid = diffs.size() / 2;
        long median = diffs.size() % 2 == 1 ? diffs.get(mid) : Math.round((diffs.get(mid - 1) + diffs.get(mid)) / 2.0);
        return new Result(nameA, nameB, diffs.size(), unpairedA, unpairedB, median, diffs.get(0),
                diffs.get(diffs.size() - 1));
    }

    private static List<Long> sorted(List<Long> times) {
        List<Long> copy = new ArrayList<>(times);
        Collections.sort(copy);
        return copy;
    }

    /** Prints the comparison, or says why none can be made when a recording has no {@code .times} file. */
    static void print(PrintStream out, String nameA, DumpSummary a, String nameB, DumpSummary b, long windowMs) {
        out.println();
        out.println("Comparing " + nameB + " with " + nameA);
        if (!a.hasWallClock() || !b.hasWallClock()) {
            out.println("  Both recordings need their .times file to be compared.");
            return;
        }
        Result r = compare(nameA, a, nameB, b, windowMs);
        out.printf("  Paired passings: %d (within %d ms); only in %s: %d; only in %s: %d%n", r.paired(), windowMs,
                nameA, r.unpairedA(), nameB, r.unpairedB());
        if (r.paired() == 0) {
            out.println("  No passings paired, so there is no offset to report.");
            return;
        }
        out.printf("  Arrival time of %s minus %s: median %d ms, from %d to %d ms (spread %d ms)%n", nameB, nameA,
                r.medianMs(), r.minMs(), r.maxMs(), r.maxMs() - r.minMs());
        out.println("  On the same loop, set " + nameB + "'s clock offset to about " + (-r.medianMs())
                + " ms to line it up with " + nameA + ". This is an estimate: RCTimingControl pins each decoder at its"
                + " first passing, so the delay of that one passing is kept until the next restart, and a wide spread"
                + " means the offset is not steady.");
    }
}
