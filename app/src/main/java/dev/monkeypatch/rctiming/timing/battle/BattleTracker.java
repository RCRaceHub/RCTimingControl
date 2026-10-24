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
 * {@value #HOLD_LAPS} laps after it drops outside the gap, so it does not flicker on and off. A lap count that goes
 * back (a marshal's lap taken away) starts the pair's trend again. Chained pairs (A-B, B-C) are joined into one
 * group, a train. Gaps are those of the scored order, so time penalties are included and nothing here reads the
 * database.
 */
public class BattleTracker {

    /** The largest gap, in milliseconds, between adjacent cars that starts a battle. */
    public static final long DEFAULT_GAP_MS = 2000;
    /** A gap changing by more than this many milliseconds a lap is closing or growing rather than holding. */
    static final long TREND_MS_PER_LAP = 150;
    /** A gap closing by this many milliseconds a lap or more marks a car that is gaining fast. */
    static final long GAINING_FAST_MS_PER_LAP = 300;
    /** How many of the chaser's laps are judged for the trend. */
    static final int TREND_SAMPLES = 4;
    /** Laps the chaser may run outside the gap before the pair stops being a battle. */
    static final int HOLD_LAPS = 2;

    private record Sample(int chaserLaps, long gapMs) {
    }

    private static final class PairHistory {
        final List<Sample> samples = new ArrayList<>();
        int lastCloseLaps;
    }

    private record Link(LiveTimingRowDto ahead, LiveTimingRowDto chaser, BattleLinkDto dto) {
    }

    private final long gapMs;
    /** Keyed by {@code aheadEntryId:chaserEntryId}. */
    private Map<String, PairHistory> history = new HashMap<>();

    public BattleTracker(long gapMs) {
        this.gapMs = gapMs;
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
        if (gapMs <= 0) {
            history = next;
            return List.of();
        }

        Map<Integer, LiveTimingRowDto> byPosition = new HashMap<>();
        rows.forEach(row -> byPosition.put(row.position(), row));
        for (LiveTimingRowDto chaser : rows) {
            LiveTimingRowDto ahead = byPosition.get(chaser.position() - 1);
            if (ahead == null || chaser.finished() || ahead.finished() || chaser.intervalLapsDown() > 0
                    || chaser.gapToAheadMs() == null) {
                continue;
            }
            String key = ahead.entryId() + ":" + chaser.entryId();
            PairHistory previous = history.get(key);
            PairHistory pair = new PairHistory();
            if (previous != null && lastLaps(previous) <= chaser.lapsCompleted()) {
                pair.samples.addAll(previous.samples);
                pair.lastCloseLaps = previous.lastCloseLaps;
            } else if (previous != null) {
                pair.lastCloseLaps = chaser.lapsCompleted();
            }
            if (pair.samples.isEmpty() || lastLaps(pair) != chaser.lapsCompleted()) {
                pair.samples.add(new Sample(chaser.lapsCompleted(), chaser.gapToAheadMs()));
            }
            boolean inRange = chaser.gapToAheadMs() <= gapMs;
            if (inRange) {
                pair.lastCloseLaps = chaser.lapsCompleted();
            } else if (previous == null || chaser.lapsCompleted() - pair.lastCloseLaps > HOLD_LAPS) {
                // Never inside the gap, or out of it too long to remember
                continue;
            }
            while (pair.samples.size() > TREND_SAMPLES) {
                pair.samples.remove(0);
            }
            next.put(key, pair);
            links.add(new Link(ahead, chaser, linkOf(pair, chaser.gapToAheadMs())));
        }
        history = next;
        return group(links);
    }

    private static int lastLaps(PairHistory pair) {
        return pair.samples.get(pair.samples.size() - 1).chaserLaps();
    }

    private static BattleLinkDto linkOf(PairHistory pair, long gapMs) {
        Sample first = pair.samples.get(0);
        Sample last = pair.samples.get(pair.samples.size() - 1);
        Long rate = last.chaserLaps() > first.chaserLaps()
                ? Math.round((double) (last.gapMs() - first.gapMs()) / (last.chaserLaps() - first.chaserLaps()))
                : null;
        BattleTrend trend = rate == null ? BattleTrend.UNKNOWN
                : rate <= -TREND_MS_PER_LAP ? BattleTrend.CLOSING
                : rate >= TREND_MS_PER_LAP ? BattleTrend.GROWING
                : BattleTrend.HOLDING;
        return new BattleLinkDto(gapMs, rate, trend, rate != null && rate <= -GAINING_FAST_MS_PER_LAP);
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
