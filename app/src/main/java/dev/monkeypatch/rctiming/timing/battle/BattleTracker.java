package dev.monkeypatch.rctiming.timing.battle;

import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spots on-track battles in a race (#505): two adjacent cars on the same lap within a set gap, and whether that gap
 * is closing or growing. One tracker belongs to one race's {@link dev.monkeypatch.rctiming.timing.LiveRaceState},
 * which calls it under its own lock on the timing thread, so it needs none of its own.
 *
 * <p>The gap between two cars changes only as they cross the line, so it is sampled once per lap of the car behind,
 * and the trend is the average change per lap over its last {@value #TREND_SAMPLES} samples. A pair stays a battle for
 * the hold (in {@link Settings}) laps after it drops outside the gap, so it does not flicker on and off. A lap count
 * that goes back (a marshal's lap taken away) starts the pair's trend again. Between the car ahead crossing the line
 * and the car behind doing so the order gives the car behind no gap; the pair keeps its last gap and history through
 * that. Chained pairs (A-B, B-C) are joined into one group, a train. Gaps are those of the scored order, so time
 * penalties are included and nothing here reads the database. The club's thresholds can change mid-race
 * ({@link #setSettings}) and apply from the next update.
 */
public class BattleTracker {

    /**
     * What decides a battle (#505), all in milliseconds except the lap count.
     *
     * @param gapMs         the largest gap between adjacent cars that starts a battle; 0 or less switches battles off
     * @param trendMsPerLap a gap changing by more than this each lap is closing or growing rather than holding
     * @param fastMsPerLap  a gap closing by this much or more each lap marks a car that is gaining fast
     * @param holdLaps      laps the chaser may run outside the gap before the pair stops being a battle
     */
    public record Settings(long gapMs, long trendMsPerLap, long fastMsPerLap, int holdLaps) {

        /** The settings until the club changes them. */
        public static Settings defaults() {
            return new Settings(2000, 150, 300, 2);
        }
    }

    /** How many of the chaser's laps are judged for the trend. */
    static final int TREND_SAMPLES = 4;

    private record Sample(int chaserLaps, long gapMs) {
    }

    private static final class PairHistory {
        final List<Sample> samples = new ArrayList<>();
        int lastCloseLaps;
    }

    private record Link(LiveTimingRowDto ahead, LiveTimingRowDto chaser, BattleLinkDto dto) {
    }

    private volatile Settings settings;
    /** Keyed by {@code aheadEntryId:chaserEntryId}. */
    private Map<String, PairHistory> history = new HashMap<>();

    public BattleTracker(Settings settings) {
        this.settings = settings;
    }

    /** Changes the settings for the next update; the club's thresholds were edited mid-race. */
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    /**
     * Takes one timing update in and returns the battles it shows.
     *
     * @param rows the race's rows in running order, as {@code LiveRaceState.calculatePositions} builds them
     * @return the groups, gaining-fast first and then by the tightest gap; empty when no cars are close or the gap is
     *         0 or less
     */
    public List<BattleGroupDto> advance(List<LiveTimingRowDto> rows) {
        Map<String, PairHistory> next = new HashMap<>();
        List<Link> links = new ArrayList<>();
        Settings current = settings;
        if (current.gapMs() <= 0) {
            history = next;
            return List.of();
        }

        Map<Integer, LiveTimingRowDto> byPosition = new HashMap<>();
        rows.forEach(row -> byPosition.put(row.position(), row));
        for (LiveTimingRowDto chaser : rows) {
            LiveTimingRowDto ahead = byPosition.get(chaser.position() - 1);
            if (ahead == null || chaser.finished() || ahead.finished()) {
                continue;
            }
            String key = ahead.entryId() + ":" + chaser.entryId();
            PairHistory previous = history.get(key);
            // A lap count that goes back (a marshal's -1) makes the old laps meaningless: start the pair again
            if (previous != null && lastLaps(previous) > chaser.lapsCompleted()) {
                previous = null;
            }
            if (chaser.intervalLapsDown() > 0 || chaser.gapToAheadMs() == null) {
                // The car ahead has crossed and the chaser has not, so for a moment the order gives no gap: keep the
                // pair and its last gap rather than lose its history every lap
                if (previous != null && chaser.lapsCompleted() - previous.lastCloseLaps <= current.holdLaps()) {
                    next.put(key, previous);
                    long lastGapMs = previous.samples.get(previous.samples.size() - 1).gapMs();
                    links.add(new Link(ahead, chaser, linkOf(previous, lastGapMs, current)));
                }
                continue;
            }
            PairHistory pair = new PairHistory();
            if (previous != null) {
                pair.samples.addAll(previous.samples);
                pair.lastCloseLaps = previous.lastCloseLaps;
            }
            if (pair.samples.isEmpty() || lastLaps(pair) != chaser.lapsCompleted()) {
                pair.samples.add(new Sample(chaser.lapsCompleted(), chaser.gapToAheadMs()));
            }
            boolean inRange = chaser.gapToAheadMs() <= current.gapMs();
            if (inRange) {
                pair.lastCloseLaps = chaser.lapsCompleted();
            } else if (previous == null || chaser.lapsCompleted() - pair.lastCloseLaps > current.holdLaps()) {
                // Never inside the gap, or out of it too long to remember
                continue;
            }
            while (pair.samples.size() > TREND_SAMPLES) {
                pair.samples.remove(0);
            }
            next.put(key, pair);
            links.add(new Link(ahead, chaser, linkOf(pair, chaser.gapToAheadMs(), current)));
        }
        history = next;
        return group(links);
    }

    private static int lastLaps(PairHistory pair) {
        return pair.samples.get(pair.samples.size() - 1).chaserLaps();
    }

    private static BattleLinkDto linkOf(PairHistory pair, long gapMs, Settings current) {
        Sample first = pair.samples.get(0);
        Sample last = pair.samples.get(pair.samples.size() - 1);
        Long rate = last.chaserLaps() > first.chaserLaps()
                ? Math.round((double) (last.gapMs() - first.gapMs()) / (last.chaserLaps() - first.chaserLaps()))
                : null;
        BattleTrend trend = rate == null ? BattleTrend.UNKNOWN
                : rate < 0 && rate <= -current.trendMsPerLap() ? BattleTrend.CLOSING
                : rate > 0 && rate >= current.trendMsPerLap() ? BattleTrend.GROWING
                : BattleTrend.HOLDING;
        return new BattleLinkDto(gapMs, rate, trend, rate != null && rate <= -current.fastMsPerLap());
    }

    /** Joins links that share a car, front of the field first, then orders the groups. */
    private static List<BattleGroupDto> group(List<Link> links) {
        List<Link> ordered = new ArrayList<>(links);
        ordered.sort(Comparator.comparingInt(link -> link.ahead().position()));
        List<List<Link>> groups = new ArrayList<>();
        List<Link> current = null;
        for (Link link : ordered) {
            if (current != null && current.get(current.size() - 1).chaser().entryId() == link.ahead().entryId()) {
                current.add(link);
            } else {
                current = new ArrayList<>(List.of(link));
                groups.add(current);
            }
        }
        List<BattleGroupDto> result = new ArrayList<>();
        for (List<Link> group : groups) {
            List<BattleCarDto> cars = new ArrayList<>();
            cars.add(carOf(group.get(0).ahead()));
            group.forEach(link -> cars.add(carOf(link.chaser())));
            List<BattleLinkDto> dtos = group.stream().map(Link::dto).toList();
            result.add(new BattleGroupDto(cars, dtos, dtos.stream().anyMatch(BattleLinkDto::gainingFast)));
        }
        result.sort(Comparator
                .comparing((BattleGroupDto g) -> !g.gainingFast())
                .thenComparingLong(g -> g.links().stream().mapToLong(BattleLinkDto::gapMs).min().orElse(0)));
        return result;
    }

    private static BattleCarDto carOf(LiveTimingRowDto row) {
        return new BattleCarDto(row.entryId(), row.position(), row.driverName(), row.carNumber());
    }
}
