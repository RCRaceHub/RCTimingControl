package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Works out the qualifying order of an event class from the results stored when its qualifying heats
 * finished, so the order cannot be made up by whoever asks for the finals to be seeded (#137). The stored
 * results already have lap penalties and marshal laps applied; time penalties change only the total time,
 * which the order does not use.
 *
 * <p>The rule is the one the finals seeding has always used: total laps over all the class's qualifying
 * heats, most first, then the best single lap, quickest first, then the entry id so that exact ties come out
 * the same every time. It does not look at the class's configured qualifying type (FORMAT-09). Only finished
 * heats count, an abandoned heat's laps do not, and everyone who was on the grid of a finished heat is ranked,
 * with no laps if they never crossed the line. Withdrawn entries are left out.
 */
@Service
public class QualifyingStandingsService {

    /** The best lap of a driver with no timed lap, which sorts behind every real one. */
    private static final long NO_LAP = Long.MAX_VALUE;

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final EntryRepository entryRepository;
    private final ResultSnapshotJson snapshotJson;

    public QualifyingStandingsService(RaceRepository raceRepository,
                                      RaceEntryRepository raceEntryRepository,
                                      ResultSnapshotRepository resultSnapshotRepository,
                                      EntryRepository entryRepository,
                                      ResultSnapshotJson snapshotJson) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.entryRepository = entryRepository;
        this.snapshotJson = snapshotJson;
    }

    /**
     * Entry ids in qualifying order, best first, for everyone on the grid of one of the class's finished
     * qualifying heats. Empty when no qualifying heat has finished.
     */
    public List<Long> standingsFor(Long eventClassId) {
        return rankedResultsFor(eventClassId).stream().map(QualifyingResult::entryId).toList();
    }

    /** The same order with each driver's total laps and best lap, for showing the overall qualifying table. */
    public List<QualifyingResult> rankedResultsFor(Long eventClassId) {
        Map<Long, QualifyingResult> byEntry = new LinkedHashMap<>();
        for (Race race : raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)) {
            if (race.getStatus() != RaceStatus.FINISHED) {
                continue;
            }
            // A driver who never crossed the line has no result row but still gets a place in the finals
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(race.getId())) {
                byEntry.putIfAbsent(onGrid.getEntryId(), new QualifyingResult(onGrid.getEntryId(), NO_LAP, 0));
            }
            if (race.getAbandonedAt() != null) {
                continue;
            }
            resultSnapshotRepository.findByRaceId(race.getId()).ifPresent(snapshot -> {
                var rows = snapshotJson.positions(race.getId(), snapshot.getPositionsJson());
                for (ResultSnapshotDto.ResultRow row : rows) {
                    long bestLap = row.bestLapMs() == null ? NO_LAP : row.bestLapMs();
                    byEntry.merge(row.entryId(), new QualifyingResult(row.entryId(), bestLap, row.lapsCompleted()),
                            QualifyingResult::plus);
                }
            });
        }
        Set<Long> withdrawn = entryRepository.findByEventClassIdAndStatus(eventClassId, EntryStatus.WITHDRAWN)
                .stream().map(Entry::getId).collect(Collectors.toSet());
        byEntry.keySet().removeAll(withdrawn);
        return rankResults(byEntry.values());
    }

    /** Laps completed, most first, then best lap, quickest first, then entry id. */
    static List<Long> rank(Collection<QualifyingResult> results) {
        return rankResults(results).stream().map(QualifyingResult::entryId).toList();
    }

    private static List<QualifyingResult> rankResults(Collection<QualifyingResult> results) {
        return results.stream()
                .sorted(Comparator
                        .comparingInt(QualifyingResult::lapsCompleted).reversed()
                        .thenComparingLong(QualifyingResult::bestLapMs)
                        .thenComparing(QualifyingResult::entryId))
                .toList();
    }

    /**
     * One driver's qualifying result across the class's heats.
     *
     * @param bestLapMs     best single lap in milliseconds across all heats; {@code Long.MAX_VALUE} for none
     * @param lapsCompleted total laps across all heats
     */
    public record QualifyingResult(Long entryId, long bestLapMs, int lapsCompleted) {

        /** Whether the driver set a timed lap in any heat. */
        public boolean hasBestLap() {
            return bestLapMs != NO_LAP;
        }

        QualifyingResult plus(QualifyingResult other) {
            return new QualifyingResult(entryId, Math.min(bestLapMs, other.bestLapMs),
                    lapsCompleted + other.lapsCompleted);
        }
    }
}
