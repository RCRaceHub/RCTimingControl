package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.boards.LiveQualifyingDto;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the two tables of the live top board for a final: the order of the leg on track (or the leg that just
 * finished), and, when the final is run in legs, its combined order over the legs run so far with the leg on track
 * counted from its live positions as if it ended now. A final of one leg has only its own order. It only reads, and is
 * called from the anonymous board endpoint on a request thread, which hands it the leg's live positions (the live
 * timing state lives in the {@code timing} package, which this class does not depend on); a stopped leg is shown
 * like a running one. Scoring is {@link FinalLegScoring}, as for the A final's combined result on the finals board.
 * Only the A final has legs today, so every other final has the one table.
 */
@Service
public class LiveFinalService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;
    private final PublicNames publicNames;

    public LiveFinalService(RaceRepository raceRepository,
                            RaceEntryRepository raceEntryRepository,
                            EntryRepository entryRepository,
                            CompetitorRepository competitorRepository,
                            EventClassRepository eventClassRepository,
                            RaceFormatService raceFormatService,
                            ResultSnapshotRepository resultSnapshotRepository,
                            ResultSnapshotJson snapshotJson,
                            PublicNames publicNames) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
        this.publicNames = publicNames;
    }

    /**
     * The tables of a final's leg.
     *
     * @param finalLetter the final's letter
     * @param leg         1-based number of the leg being shown
     * @param legs        how many legs the final has
     * @param rows        the final's order: the combined order over its legs, or the leg's own order for a final of
     *                    one leg, where {@code points} is null
     * @param roundRows   the leg's own order; empty for a final of one leg, whose only table is {@code rows}
     */
    public record Tables(String finalLetter, int leg, int legs, List<LiveQualifyingDto.Row> rows,
                         List<LiveQualifyingDto.RoundRow> roundRows) {}

    /**
     * The tables for a final's leg. {@code liveRows} are the leg's live positions while it runs, or null once it has
     * finished, when its stored result is used. Empty when the race is not a final or has no positions yet (a leg
     * that has started but has had no passing, or a finished leg with no stored result). Names are as the public may
     * see them (#265). Drivers with no run in a leg score one more than the number of drivers, as in the combined
     * result.
     */
    @ReadTransaction
    public Optional<Tables> forLeg(long raceId, List<LiveTimingRowDto> liveRows) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null || race.getFinalLetter() == null) {
            return Optional.empty();
        }
        List<Race> legs = raceRepository.findByEventClassIdAndRoundType(race.getEventClassId(), RoundType.FINAL)
                .stream().filter(r -> race.getFinalLetter().equals(r.getFinalLetter()))
                .sorted(Comparator.comparingInt(Race::getHeatNumber)).toList();
        int legNumber = 0;
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).getId().equals(raceId)) {
                legNumber = i + 1;
            }
        }
        if (legNumber == 0) {
            return Optional.empty();
        }
        List<ResultSnapshotDto.ResultRow> legOrder = liveRows != null
                ? liveRows.stream().map(LiveFinalService::toResultRow).toList() : storedRows(raceId);
        if (legOrder.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> inLeg = new LinkedHashSet<>();
        legOrder.forEach(r -> inLeg.add(r.entryId()));
        PublicNames.Snapshot names = publicNames.snapshot();
        Map<Long, String> carNumbers = carNumbers(legs);

        if (legs.size() < 2) {
            List<LiveQualifyingDto.Row> rows = new ArrayList<>();
            for (ResultSnapshotDto.ResultRow r : legOrder) {
                rows.add(new LiveQualifyingDto.Row(rows.size() + 1, null, r.entryId(),
                        driverName(r.entryId(), carNumbers.get(r.entryId()), names), carNumbers.get(r.entryId()),
                        null, r.lapsCompleted(), r.bestLapMs(), liveRows != null, false));
            }
            return Optional.of(new Tables(race.getFinalLetter(), 1, 1, rows, List.of()));
        }

        List<LiveQualifyingDto.RoundRow> roundRows = new ArrayList<>();
        for (ResultSnapshotDto.ResultRow r : legOrder) {
            roundRows.add(new LiveQualifyingDto.RoundRow(roundRows.size() + 1, null, r.entryId(),
                    driverName(r.entryId(), carNumbers.get(r.entryId()), names), carNumbers.get(r.entryId()),
                    r.lapsCompleted(),
                    r.lapsCompleted() == 0 || r.totalTimeMs() == Long.MAX_VALUE ? null : r.totalTimeMs(),
                    r.bestLapMs(), liveRows != null));
        }

        // Everyone on the first leg's grid is in the final, so a driver who has not run a leg still scores for it
        Set<Long> drivers = new LinkedHashSet<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(legs.get(0).getId())
                .forEach(e -> drivers.add(e.getEntryId()));
        drivers.addAll(inLeg);
        EventClass eventClass = eventClassRepository.findById(race.getEventClassId()).orElse(null);
        int legsCounted = eventClass == null ? 0 : raceFormatService.legsCounted(eventClass);

        List<Map<Long, FinalLegScoring.Run>> runs = new ArrayList<>();
        List<Map<Long, FinalLegScoring.Run>> runsWithoutThisLeg = new ArrayList<>();
        for (Race leg : legs) {
            boolean thisLeg = leg.getId().equals(raceId);
            Map<Long, FinalLegScoring.Run> legRuns;
            if (thisLeg) {
                legRuns = runsOf(legOrder);
            } else if (leg.getStatus() == RaceStatus.FINISHED && leg.getAbandonedAt() == null) {
                List<ResultSnapshotDto.ResultRow> stored = storedRows(leg.getId());
                stored.forEach(r -> drivers.add(r.entryId()));
                legRuns = runsOf(stored);
            } else {
                continue;
            }
            runs.add(legRuns);
            if (!thisLeg) {
                runsWithoutThisLeg.add(legRuns);
            }
        }
        // With no other leg run there is no earlier order to compare with
        Map<Long, Integer> before = new HashMap<>();
        if (!runsWithoutThisLeg.isEmpty()) {
            List<FinalLegScoring.Standing> baseline = FinalLegScoring.rank(runsWithoutThisLeg, drivers, legsCounted);
            for (int i = 0; i < baseline.size(); i++) {
                before.put(baseline.get(i).entryId(), i + 1);
            }
        }
        List<FinalLegScoring.Standing> standings = FinalLegScoring.rank(runs, drivers, legsCounted);
        Map<Long, Long> bestLap = new HashMap<>();
        legOrder.forEach(r -> bestLap.put(r.entryId(), r.bestLapMs()));
        List<LiveQualifyingDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            FinalLegScoring.Standing s = standings.get(i);
            rows.add(new LiveQualifyingDto.Row(i + 1, before.get(s.entryId()), s.entryId(),
                    driverName(s.entryId(), carNumbers.get(s.entryId()), names), carNumbers.get(s.entryId()),
                    s.points(), s.laps(), bestLap.get(s.entryId()),
                    liveRows != null && inLeg.contains(s.entryId()), false));
        }
        return Optional.of(new Tables(race.getFinalLetter(), legNumber, legs.size(), rows, roundRows));
    }

    private static Map<Long, FinalLegScoring.Run> runsOf(List<ResultSnapshotDto.ResultRow> order) {
        Map<Long, FinalLegScoring.Run> runs = new LinkedHashMap<>();
        for (ResultSnapshotDto.ResultRow r : order) {
            runs.put(r.entryId(), new FinalLegScoring.Run(r.lapsCompleted(), r.totalTimeMs()));
        }
        return runs;
    }

    /** A live row as a result row; a car with no time yet is the slowest of its laps. */
    private static ResultSnapshotDto.ResultRow toResultRow(LiveTimingRowDto row) {
        return new ResultSnapshotDto.ResultRow(row.position(), row.entryId(), null, row.driverName(),
                row.carNumber(), row.lapsCompleted(), row.totalTimeMs() == null ? Long.MAX_VALUE : row.totalTimeMs(),
                row.bestLapMs(), row.gapToLeaderMs());
    }

    private List<ResultSnapshotDto.ResultRow> storedRows(long raceId) {
        return resultSnapshotRepository.findByRaceId(raceId)
                .map(s -> snapshotJson.positions(raceId, s.getPositionsJson())).orElse(List.of());
    }

    private Map<Long, String> carNumbers(List<Race> legs) {
        Map<Long, String> carNumbers = new HashMap<>();
        for (Race leg : legs) {
            for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(leg.getId())) {
                if (raceEntry.getCarNumber() != null) {
                    carNumbers.put(raceEntry.getEntryId(), raceEntry.getCarNumber().toString());
                }
            }
        }
        return carNumbers;
    }

    private String driverName(Long entryId, String carNumber, PublicNames.Snapshot names) {
        return entryRepository.findById(entryId)
                .flatMap(e -> competitorRepository.findById(e.getCompetitorId()))
                .map(c -> names.of(c.getId(), c.getDisplayName(), carNumber))
                .orElse(carNumber == null ? "Driver" : "Car " + carNumber);
    }
}
