package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orders a Shikabane heat's result. Cars still alive at the end keep their timed order (most laps, then earliest
 * finish); the dead are placed after them, the last car to die ahead of the one that died before it, so the first
 * car eliminated is last. A car's place is then its heat penalty points (1 for the winner up to the number of cars
 * for the first to die), which low-points qualifying already takes as the round score. Pure, no Spring or database.
 */
final class ShikabaneOrdering {

    private ShikabaneOrdering() {}

    /**
     * Reorders {@code rows} and renumbers their positions from 1. With nothing eliminated the list comes back as it
     * was; otherwise it is a new list and the rows are copied with their new position, and the gap to the leader is
     * kept as it was timed.
     *
     * @param rows               the heat's rows in timed order, with a row for every eliminated entry
     * @param eliminatedInOrder  entry ids of the cars dead at the end, first to die first
     */
    static List<ResultRow> apply(List<ResultRow> rows, List<Long> eliminatedInOrder) {
        if (eliminatedInOrder.isEmpty()) {
            return rows;
        }
        Map<Long, ResultRow> byEntry = rows.stream()
                .collect(Collectors.toMap(ResultRow::entryId, Function.identity(), (a, b) -> a));
        Set<Long> deadIds = new HashSet<>(eliminatedInOrder);
        List<ResultRow> ordered = new ArrayList<>();
        for (ResultRow row : rows) {
            if (!deadIds.contains(row.entryId())) {
                ordered.add(row);
            }
        }
        for (int i = eliminatedInOrder.size() - 1; i >= 0; i--) {
            ResultRow dead = byEntry.get(eliminatedInOrder.get(i));
            if (dead != null) {
                ordered.add(dead);
            }
        }
        List<ResultRow> renumbered = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            ResultRow row = ordered.get(i);
            renumbered.add(new ResultRow(i + 1, row.entryId(), row.competitorId(), row.driverName(), row.carNumber(),
                    row.lapsCompleted(), row.totalTimeMs(), row.bestLapMs(), row.gapToLeaderMs())
                    .withDnf(row.dnf()));
        }
        return renumbered;
    }
}
