package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.PracticeRanking;
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
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.Overlay;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.ProjectedResults;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.QualifyingResult;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService.RoundStanding;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds the rows of the live qualifying table (#480) for one qualifying heat: the class's low-points order with
 * the heat counted, and where each driver stood without it, so a board can show who is moving. It only reads, and
 * is called from the anonymous board endpoint, which hands it the heat's live positions (the live timing state
 * lives in the {@code timing} package, which this class does not depend on).
 */
@Service
public class LiveQualifyingService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final QualifyingStandingsService qualifyingStandingsService;
    private final PublicNames publicNames;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;

    public LiveQualifyingService(RaceRepository raceRepository,
                                 RaceEntryRepository raceEntryRepository,
                                 EntryRepository entryRepository,
                                 CompetitorRepository competitorRepository,
                                 QualifyingStandingsService qualifyingStandingsService,
                                 PublicNames publicNames,
                                 ResultSnapshotRepository resultSnapshotRepository,
                                 ResultSnapshotJson snapshotJson) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
        this.publicNames = publicNames;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
    }

    /**
     * The table for a qualifying heat. {@code liveRows} are the heat's live positions while it runs, or null once
     * it has finished, when its stored result is counted. Empty when the heat is not a qualifying heat or its class
     * is not ranked by low points. Drivers whose only heat is still to come are not in a first round's table, so
     * they show as waiting only from the second round on. Names are as the public may see them (#265).
     *
     * <p>The round table counts the heat the same way as the overall one, so both move with every lap. Drivers whose
     * heat in the round has not run are left out of the round table until it does.
     */
    @ReadTransaction
    public Optional<Tables> forHeat(long raceId, List<LiveTimingRowDto> liveRows) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            return Optional.empty();
        }
        Long eventClassId = race.getEventClassId();
        boolean qualifier = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)
                .stream().anyMatch(h -> h.getId().equals(raceId));
        if (!qualifier) {
            return Optional.empty();
        }
        // A heat that has started but has no live positions yet (before its first passing, or after a restart) is
        // left out rather than scored as every driver on no laps
        Overlay counted = liveRows == null ? Overlay.stored(raceId)
                : liveRows.isEmpty() ? Overlay.without(raceId) : Overlay.live(raceId, liveRows);
        ProjectedResults after = qualifyingStandingsService.projectedResultsFor(eventClassId, counted);
        if (after.results().isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Integer> before = new HashMap<>();
        List<QualifyingResult> baseline = qualifyingStandingsService
                .projectedResultsFor(eventClassId, Overlay.without(raceId)).results();
        for (int i = 0; i < baseline.size(); i++) {
            before.put(baseline.get(i).entryId(), i + 1);
        }
        Set<Long> inHeat = new HashSet<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).forEach(e -> inHeat.add(e.getEntryId()));
        Map<Long, String> carNumbers = carNumbers(eventClassId, RoundType.QUALIFIER);
        PublicNames.Snapshot names = publicNames.snapshot();

        List<LiveQualifyingDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < after.results().size(); i++) {
            QualifyingResult result = after.results().get(i);
            Long entryId = result.entryId();
            rows.add(new LiveQualifyingDto.Row(i + 1, before.get(entryId), entryId, driverName(entryId, carNumbers.get(entryId), names),
                    carNumbers.get(entryId), result.points(), result.lapsCompleted(),
                    result.hasBestLap() ? result.bestLapMs() : null, inHeat.contains(entryId),
                    after.waiting().contains(entryId)));
        }
        return Optional.of(new Tables(rows, roundRows(race, counted, carNumbers, names, inHeat)));
    }

    /**
     * The tables for a practice heat (the board's "Practice 1"): the order inside the heat's round, and the overall
     * order of each driver's best across every practice heat of the class so far, both ranked by {@code ranking}.
     * {@code liveLapTimes} are the lap times of each car in the heat on track by entry id, or null once the heat has
     * finished, when its stored lap history is used. Heats that have not finished (other than the one shown) are
     * left out. Empty when the heat is not a practice heat or no lap has been timed. A driver with no run of the
     * ranked length (2 laps when 3 are wanted) is listed after those with one, with no time.
     *
     * <p>Both tables show who moved against the same tables without this heat. The ranked time is carried in
     * {@code bestLapMs} of each row, and {@code points} is null.
     */
    @ReadTransaction
    public Optional<Tables> forPracticeHeat(long raceId, Map<Long, List<Long>> liveLapTimes, PracticeRanking ranking) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            return Optional.empty();
        }
        Long eventClassId = race.getEventClassId();
        List<Race> heats = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.PRACTICE);
        if (heats.stream().noneMatch(h -> h.getId().equals(raceId))) {
            return Optional.empty();
        }
        Map<Long, Map<Long, List<Long>>> lapsByHeat = new LinkedHashMap<>();
        Map<Long, Long> roundOfHeat = new HashMap<>();
        for (Race heat : heats) {
            if (heat.getId().equals(raceId) && liveLapTimes != null) {
                lapsByHeat.put(heat.getId(), liveLapTimes);
            } else if (heat.getStatus() == RaceStatus.FINISHED) {
                lapsByHeat.put(heat.getId(), storedLapTimes(heat.getId()));
            } else {
                continue;
            }
            roundOfHeat.put(heat.getId(), heat.getRoundId());
        }
        List<PracticeStanding> overall = practiceOrder(lapsByHeat, ranking, h -> true);
        if (overall.isEmpty()) {
            return Optional.empty();
        }
        Long roundId = race.getRoundId();
        List<PracticeStanding> inRound = practiceOrder(lapsByHeat, ranking, h -> Objects.equals(roundId, roundOfHeat.get(h)));
        Map<Long, Integer> overallBefore = places(practiceOrder(lapsByHeat, ranking, h -> h != raceId));
        Map<Long, Integer> roundBefore = places(practiceOrder(lapsByHeat, ranking,
                h -> h != raceId && Objects.equals(roundId, roundOfHeat.get(h))));

        Set<Long> inHeat = new HashSet<>();
        raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).forEach(e -> inHeat.add(e.getEntryId()));
        Map<Long, String> carNumbers = carNumbers(eventClassId, RoundType.QUALIFIER);
        carNumbers.putAll(carNumbers(eventClassId, RoundType.PRACTICE));
        PublicNames.Snapshot names = publicNames.snapshot();

        List<LiveQualifyingDto.Row> rows = new ArrayList<>();
        for (int i = 0; i < overall.size(); i++) {
            PracticeStanding s = overall.get(i);
            rows.add(new LiveQualifyingDto.Row(i + 1, overallBefore.get(s.entryId()), s.entryId(),
                    driverName(s.entryId(), carNumbers.get(s.entryId()), names), carNumbers.get(s.entryId()), null,
                    s.laps(), s.rankedMs(), inHeat.contains(s.entryId()), false));
        }
        List<LiveQualifyingDto.RoundRow> roundRows = new ArrayList<>();
        for (int i = 0; i < inRound.size(); i++) {
            PracticeStanding s = inRound.get(i);
            roundRows.add(new LiveQualifyingDto.RoundRow(i + 1, roundBefore.get(s.entryId()), s.entryId(),
                    driverName(s.entryId(), carNumbers.get(s.entryId()), names), carNumbers.get(s.entryId()),
                    s.laps(), null, s.rankedMs(), inHeat.contains(s.entryId())));
        }
        return Optional.of(new Tables(rows, roundRows));
    }

    /** One driver's practice laps and ranked time over the heats counted. */
    private record PracticeStanding(long entryId, int laps, Long rankedMs) {}

    /**
     * The drivers with a timed lap in the heats {@code counted} selects, best first: by ranked time, drivers with no
     * qualifying run after those with one, then more laps, then entry id. A driver's ranked time is their best over
     * the single heats, so a run never joins laps of two heats.
     */
    private static List<PracticeStanding> practiceOrder(Map<Long, Map<Long, List<Long>>> lapsByHeat,
                                                        PracticeRanking ranking,
                                                        Predicate<Long> counted) {
        Map<Long, Integer> laps = new HashMap<>();
        Map<Long, Long> ranked = new HashMap<>();
        lapsByHeat.forEach((heatId, byEntry) -> {
            if (!counted.test(heatId)) {
                return;
            }
            byEntry.forEach((entryId, times) -> {
                if (times.isEmpty()) {
                    return;
                }
                laps.merge(entryId, times.size(), Integer::sum);
                Long best = ranking.best(times);
                ranked.merge(entryId, best == null ? Long.MAX_VALUE : best, Math::min);
            });
        });
        List<PracticeStanding> standings = new ArrayList<>();
        laps.forEach((entryId, count) -> {
            Long best = ranked.get(entryId);
            standings.add(new PracticeStanding(entryId, count, best == null || best == Long.MAX_VALUE ? null : best));
        });
        standings.sort(Comparator
                .comparing((PracticeStanding s) -> s.rankedMs() == null)
                .thenComparing(s -> s.rankedMs() == null ? 0L : s.rankedMs())
                .thenComparing(PracticeStanding::laps, Comparator.reverseOrder())
                .thenComparing(PracticeStanding::entryId));
        return standings;
    }

    private static Map<Long, Integer> places(List<PracticeStanding> order) {
        Map<Long, Integer> places = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            places.put(order.get(i).entryId(), i + 1);
        }
        return places;
    }

    /** A finished heat's lap times by entry, in lap order, from its stored lap history. */
    private Map<Long, List<Long>> storedLapTimes(long raceId) {
        Map<Long, List<ResultSnapshotDto.PositionAtLap>> byEntry = new HashMap<>();
        resultSnapshotRepository.findByRaceId(raceId).ifPresent(snapshot -> {
            for (ResultSnapshotDto.PositionAtLap lap : snapshotJson.lapHistory(raceId, snapshot.getLapHistoryJson())) {
                if (lap.lapNumber() < 1) continue; // lap 0 is the grid, not a lap
                byEntry.computeIfAbsent(lap.entryId(), k -> new ArrayList<>()).add(lap);
            }
        });
        Map<Long, List<Long>> times = new HashMap<>();
        byEntry.forEach((entryId, laps) -> {
            laps.sort(Comparator.comparingInt(ResultSnapshotDto.PositionAtLap::lapNumber));
            times.put(entryId, laps.stream().map(ResultSnapshotDto.PositionAtLap::lapTimeMs).toList());
        });
        return times;
    }

    /** The overall order and the order inside the heat's round, as {@link #forHeat} builds them. */
    public record Tables(List<LiveQualifyingDto.Row> rows, List<LiveQualifyingDto.RoundRow> roundRows) {}

    private List<LiveQualifyingDto.RoundRow> roundRows(Race race, Overlay counted, Map<Long, String> carNumbers,
                                                       PublicNames.Snapshot names, Set<Long> inHeat) {
        Long eventClassId = race.getEventClassId();
        List<RoundStanding> after = qualifyingStandingsService.projectedRoundFor(eventClassId, race.getRoundId(), counted);
        Map<Long, Integer> before = new HashMap<>();
        List<RoundStanding> baseline = qualifyingStandingsService
                .projectedRoundFor(eventClassId, race.getRoundId(), Overlay.without(race.getId()));
        for (int i = 0; i < baseline.size(); i++) {
            before.put(baseline.get(i).entryId(), i + 1);
        }
        List<LiveQualifyingDto.RoundRow> rows = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            RoundStanding standing = after.get(i);
            Long entryId = standing.entryId();
            rows.add(new LiveQualifyingDto.RoundRow(i + 1, before.get(entryId), entryId,
                    driverName(entryId, carNumbers.get(entryId), names), carNumbers.get(entryId),
                    standing.lapsCompleted(),
                    standing.totalTimeMs() == Long.MAX_VALUE ? null : standing.totalTimeMs(),
                    standing.bestLapMs() == Long.MAX_VALUE ? null : standing.bestLapMs(),
                    inHeat.contains(entryId)));
        }
        return rows;
    }

    /** Each entry's car number across the class's heats of the given round type. */
    private Map<Long, String> carNumbers(Long eventClassId, RoundType type) {
        Map<Long, String> carNumbers = new HashMap<>();
        for (Race heat : raceRepository.findByEventClassIdAndRoundType(eventClassId, type)) {
            for (RaceEntry raceEntry : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
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
