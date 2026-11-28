package dev.monkeypatch.rctiming.domain.result;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Puts the cars an official marked "did not start" at the bottom of a heat's result, with no laps and no time,
 * flagged DNS, and numbers the places again from 1.
 *
 * <p>Pure: no Spring and no database. Called by {@code ResultSnapshotService} after every other correction, so a
 * DNS car stays last whatever a penalty or marshal lap did to the cars that ran. Several DNS cars keep the order
 * they have in the rows given, which is their grid order when they were added from the grid.
 */
public final class DnsOrdering {

    private DnsOrdering() {}

    /**
     * @param rows the heat's corrected rows in finishing order
     * @param dns  entry ids of the cars marked as not started; a car with no row yet is not added here
     * @return {@code rows} itself when no car is marked, otherwise a new list: the cars that ran in their order,
     *         then each DNS car with zero laps, zero time, no best lap or gap and {@code dns} set
     */
    public static List<ResultRow> apply(List<ResultRow> rows, Set<Long> dns) {
        if (dns.isEmpty()) {
            return rows;
        }
        List<ResultRow> ordered = new ArrayList<>(rows.size());
        List<ResultRow> notStarted = new ArrayList<>();
        for (ResultRow row : rows) {
            if (dns.contains(row.entryId())) {
                notStarted.add(row);
            } else {
                ordered.add(row);
            }
        }
        if (notStarted.isEmpty()) {
            return rows;
        }
        ordered.addAll(notStarted);
        List<ResultRow> numbered = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            ResultRow row = ordered.get(i);
            if (dns.contains(row.entryId())) {
                numbered.add(new ResultRow(i + 1, row.entryId(), row.competitorId(), row.driverName(),
                        row.carNumber(), 0, 0L, null, null).withDns(true));
            } else {
                numbered.add(new ResultRow(i + 1, row.entryId(), row.competitorId(), row.driverName(),
                        row.carNumber(), row.lapsCompleted(), row.totalTimeMs(), row.bestLapMs(),
                        row.gapToLeaderMs()).withDnf(row.dnf()));
            }
        }
        return numbered;
    }
}
