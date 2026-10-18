package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.PointsFinalsConfig;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.race.MarshalPenalty;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyKind;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Works out the qualifying order of an event class from the results stored when its qualifying heats
 * finished, so the order cannot be made up by whoever asks for the finals to be seeded (#137). The stored
 * results already have lap penalties and marshal laps applied; time penalties change only the total time,
 * which the order does not use.
 *
 * <p>A class whose format has the round by round qualifying type is ranked by low points over its best rounds
 * (FORMAT-09, see {@code roundByRound}). Any other class, and a round by round class before its first round has
 * finished, is ranked by total laps over all the class's qualifying heats, most first, then the best single lap,
 * quickest first, then the entry id so that exact ties come out the same every time. Only finished heats count,
 * an abandoned heat's laps do not, and everyone who was on the grid of a finished heat is ranked, with no laps if
 * they never crossed the line. Withdrawn entries are left out.
 */
@Service
public class QualifyingStandingsService {

    private static final Logger log = LoggerFactory.getLogger(QualifyingStandingsService.class);

    /** The best lap of a driver with no timed lap, which sorts behind every real one. */
    private static final long NO_LAP = Long.MAX_VALUE;

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final EntryRepository entryRepository;
    private final ResultSnapshotJson snapshotJson;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final MarshalPenaltyRepository marshalPenaltyRepository;
    private final ShikabaneHeatScores shikabaneHeatScores;

    public QualifyingStandingsService(RaceRepository raceRepository,
                                      RaceEntryRepository raceEntryRepository,
                                      ResultSnapshotRepository resultSnapshotRepository,
                                      EntryRepository entryRepository,
                                      ResultSnapshotJson snapshotJson,
                                      EventClassRepository eventClassRepository,
                                      RaceFormatService raceFormatService,
                                      MarshalPenaltyRepository marshalPenaltyRepository,
                                      ShikabaneHeatScores shikabaneHeatScores) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.entryRepository = entryRepository;
        this.snapshotJson = snapshotJson;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.marshalPenaltyRepository = marshalPenaltyRepository;
        this.shikabaneHeatScores = shikabaneHeatScores;
    }

    /**
     * Entry ids in qualifying order, best first, for everyone on the grid of one of the class's finished
     * qualifying heats. Empty when no qualifying heat has finished.
     */
    public List<Long> standingsFor(Long eventClassId) {
        return rankedResultsFor(eventClassId).stream().map(QualifyingResult::entryId).toList();
    }

    /**
     * The same order with each driver's laps and best lap (and, when ranked by low points, their points), for
     * showing the overall qualifying table.
     */
    public List<QualifyingResult> rankedResultsFor(Long eventClassId) {
        Optional<Integer> bestRounds = roundByRoundBestCount(eventClassId);
        if (bestRounds.isPresent()) {
            List<QualifyingResult> byPoints =
                    roundByRound(eventClassId, bestRounds.get(), lostBestRounds(eventClassId));
            if (!byPoints.isEmpty()) {
                return byPoints;
            }
        }
        // Not ranked by points, or no round has finished yet: order by laps so the table is never blank mid-round
        return totalLaps(eventClassId);
    }

    /**
     * The number of rounds that count when the class's format ranks qualifying round by round (low points), 0 for
     * all of them (also for a count of 0 or less); empty for any other qualifying type, which uses the total laps
     * order. A format whose settings cannot be read is treated as not round by round, so that the table and the
     * seeding still work.
     */
    private Optional<Integer> roundByRoundBestCount(Long eventClassId) {
        EventClass eventClass = eventClassRepository.findById(eventClassId).orElse(null);
        if (eventClass == null || eventClass.getConfigSnapshot() == null) {
            return Optional.empty();
        }
        RaceFormatConfig config;
        try {
            config = raceFormatService.getEffectiveConfig(eventClass);
        } catch (RuntimeException e) {
            log.warn("Could not read the format of class {}, ranking its qualifying by laps: {}", eventClassId,
                    e.getMessage());
            return Optional.empty();
        }
        QualifyingType type = switch (config) {
            case TimedRaceConfig timed -> timed.qualifyingType();
            case BumpUpConfig bumpUp -> bumpUp.qualifyingType();
            case PointsFinalsConfig points -> points.qualifyingType();
            // Heat points are the elimination order, so a Shikabane class ranks like low-points qualifying
            case ShikabaneConfig shikabane -> QualifyingType.ROUND_BY_ROUND;
        };
        if (type != QualifyingType.ROUND_BY_ROUND) {
            return Optional.empty();
        }
        return Optional.of(config instanceof BumpUpConfig bumpUp ? Math.max(0, bumpUp.bestHeatsCount()) : 0);
    }

    /**
     * Low-points qualifying ("X from Y"): in each round that is fully finished, drivers are ranked by laps, then
     * total time, over all the class's heats in the round, and score their place (1 for the round's top qualifier,
     * then 2, 3 and so on; an exact tie on laps and time shares the place). A driver missing from a round scores
     * one more than the number of drivers in it. Each driver's best {@code bestRounds} rounds count (all of them
     * when 0) and the lowest total wins; a tie goes to the most laps and then the quickest lap in the counting
     * rounds. A round with a heat still to run, or whose heats were all abandoned, is left out, since its places
     * are not settled. Empty when no round qualifies.
     */
    private List<QualifyingResult> roundByRound(Long eventClassId, int bestRounds, Map<Long, Integer> lostBest) {
        Set<Long> withdrawn = entryRepository.findByEventClassIdAndStatus(eventClassId, EntryStatus.WITHDRAWN)
                .stream().map(Entry::getId).collect(Collectors.toSet());
        Map<Long, List<Race>> heatsByRound = new LinkedHashMap<>();
        for (Race race : raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)) {
            heatsByRound.computeIfAbsent(race.getRoundId(), id -> new ArrayList<>()).add(race);
        }

        ShikabaneConfig shikabane = eventClassRepository.findById(eventClassId)
                .flatMap(raceFormatService::shikabaneConfig).orElse(null);
        // Shikabane heats are separate overall races: a driver is ranked only against the others in their heat
        Map<Long, Integer> heatOf = new HashMap<>();
        List<Map<Long, RoundScore>> rounds = new ArrayList<>();
        List<Integer> missingPoints = new ArrayList<>();
        for (List<Race> heats : heatsByRound.values()) {
            boolean finished = heats.stream().allMatch(h -> h.getStatus() == RaceStatus.FINISHED);
            boolean anyRun = heats.stream().anyMatch(h -> h.getAbandonedAt() == null);
            if (!finished || !anyRun) {
                continue;
            }
            Map<Long, RoundScore> scored = shikabane != null
                    ? scoreShikabaneRound(heats, withdrawn, shikabane) : scoreRound(heats, withdrawn);
            if (shikabane != null) {
                for (Race heat : heats) {
                    raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())
                            .forEach(e -> heatOf.putIfAbsent(e.getEntryId(), heat.getHeatNumber()));
                }
            }
            rounds.add(scored);
            missingPoints.add(scored.size() + 1);
        }

        Set<Long> drivers = new LinkedHashSet<>();
        rounds.forEach(r -> drivers.addAll(r.keySet()));
        List<QualifyingResult> results = new ArrayList<>();
        for (Long entryId : drivers) {
            QualifyingResult total = totalFor(entryId, rounds, missingPoints, bestRounds,
                    lostBest.getOrDefault(entryId, 0));
            results.add(shikabane == null ? total : new QualifyingResult(total.entryId(), total.bestLapMs(),
                    total.lapsCompleted(), total.points(), total.rounds(), heatOf.getOrDefault(entryId, 0)));
        }
        results.sort(Comparator
                .comparing((QualifyingResult r) -> r.heat() == null ? 0 : r.heat())
                .thenComparingInt(QualifyingResult::points)
                .thenComparing(Comparator.comparingInt(QualifyingResult::lapsCompleted).reversed())
                .thenComparingLong(QualifyingResult::bestLapMs)
                .thenComparing(QualifyingResult::entryId));
        return results;
    }

    /**
     * Each driver's heat points in one finished Shikabane round (see {@link ShikabaneHeatScores}), withdrawn entries
     * left out. Points are not shared on a tie, since every car has its own place; the total laps break a tie in the
     * overall order.
     */
    private Map<Long, RoundScore> scoreShikabaneRound(List<Race> heats, Set<Long> withdrawn,
                                                      ShikabaneConfig config) {
        Map<Long, RoundScore> scored = new LinkedHashMap<>();
        for (Race heat : heats) {
            for (ShikabaneHeatScores.Score score : shikabaneHeatScores.scores(heat, config)) {
                scored.put(score.entryId(), new RoundScore(score.points(), score.laps(), 0L, NO_LAP));
            }
        }
        scored.keySet().removeAll(withdrawn);
        return scored;
    }

    /** Each driver's laps, time and place in one finished round, withdrawn entries left out. */
    private Map<Long, RoundScore> scoreRound(List<Race> heats, Set<Long> withdrawn) {
        Map<Long, RoundScore> inRound = new LinkedHashMap<>();
        for (Race heat : heats) {
            // A driver who never crossed the line, or whose heat was abandoned, is last in the round. They sort
            // behind a driver with a zero-lap result row only through the entry id, since both have no laps.
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                inRound.putIfAbsent(onGrid.getEntryId(), new RoundScore(0, 0, Long.MAX_VALUE, NO_LAP));
            }
            if (heat.getAbandonedAt() == null) {
                resultSnapshotRepository.findByRaceId(heat.getId()).ifPresent(snapshot -> {
                    for (ResultSnapshotDto.ResultRow row : snapshotJson.positions(heat.getId(),
                            snapshot.getPositionsJson())) {
                        inRound.put(row.entryId(), new RoundScore(0, row.lapsCompleted(), row.totalTimeMs(),
                                row.bestLapMs() == null ? NO_LAP : row.bestLapMs()));
                    }
                });
            }
        }
        inRound.keySet().removeAll(withdrawn);
        List<Map.Entry<Long, RoundScore>> ordered = inRound.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Long, RoundScore> e) -> e.getValue().laps()).reversed()
                        .thenComparingLong(e -> e.getValue().totalTimeMs())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        Map<Long, RoundScore> scored = new LinkedHashMap<>();
        int place = 0;
        RoundScore previous = null;
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<Long, RoundScore> entry = ordered.get(i);
            RoundScore raw = entry.getValue();
            boolean tied = previous != null && previous.laps() == raw.laps()
                    && previous.totalTimeMs() == raw.totalTimeMs();
            if (!tied) {
                place = i + 1;
            }
            previous = raw;
            scored.put(entry.getKey(), new RoundScore(place, raw.laps(), raw.totalTimeMs(), raw.bestLapMs()));
        }
        return scored;
    }

    /** How many of their best rounds each driver has lost to marshal penalties, by entry id. */
    private Map<Long, Integer> lostBestRounds(Long eventClassId) {
        EventClass eventClass = eventClassRepository.findById(eventClassId).orElse(null);
        if (eventClass == null) {
            return Map.of();
        }
        Map<Long, Integer> lost = new HashMap<>();
        for (MarshalPenalty penalty : marshalPenaltyRepository
                .findByEventIdAndKind(eventClass.getEventId(), MarshalPenaltyKind.QUALIFYING_SCORE)) {
            lost.merge(penalty.getEntryId(), 1, Integer::sum);
        }
        return lost;
    }

    /**
     * One driver's points, laps and best lap over their best {@code bestRounds} rounds (all when 0), after the
     * {@code lostBest} best rounds a marshal penalty took away. The rounds that would then be missing from the
     * count are made up with the worst place scored in a round, so a penalty can never help a driver.
     */
    private static QualifyingResult totalFor(Long entryId, List<Map<Long, RoundScore>> rounds,
                                             List<Integer> missingPoints, int bestRounds, int lostBest) {
        List<RoundScore> byRound = new ArrayList<>();
        for (int i = 0; i < rounds.size(); i++) {
            byRound.add(rounds.get(i).getOrDefault(entryId,
                    new RoundScore(missingPoints.get(i), 0, Long.MAX_VALUE, NO_LAP)));
        }
        // Best rounds first; the sort is stable, so equal rounds keep their original round order
        List<Integer> bestFirst = new ArrayList<>();
        for (int i = 0; i < byRound.size(); i++) {
            bestFirst.add(i);
        }
        bestFirst.sort(Comparator.<Integer>comparingInt(i -> byRound.get(i).points())
                .thenComparing(Comparator.<Integer>comparingInt(i -> byRound.get(i).laps()).reversed()));
        int counted = bestRounds > 0 ? Math.min(bestRounds, byRound.size()) : byRound.size();
        int lost = Math.min(lostBest, byRound.size());
        List<Integer> countingRounds = bestFirst.subList(lost, Math.min(lost + counted, byRound.size()));
        List<RoundScore> counting = new ArrayList<>();
        for (int i : countingRounds) {
            counting.add(byRound.get(i));
        }
        int worst = missingPoints.stream().mapToInt(Integer::intValue).max().orElse(0);
        while (counting.size() < counted) {
            counting.add(new RoundScore(worst, 0, Long.MAX_VALUE, NO_LAP));
        }
        Set<Integer> countingSet = new HashSet<>(countingRounds);
        List<RoundPlace> places = new ArrayList<>();
        for (int i = 0; i < byRound.size(); i++) {
            places.add(new RoundPlace(byRound.get(i).points(), countingSet.contains(i)));
        }
        return new QualifyingResult(entryId,
                counting.stream().mapToLong(RoundScore::bestLapMs).min().orElse(NO_LAP),
                counting.stream().mapToInt(RoundScore::laps).sum(),
                counting.stream().mapToInt(RoundScore::points).sum(),
                places);
    }

    /** A driver's place in one finished round, and whether that round counts towards their total. */
    public record RoundPlace(int place, boolean counted) {}

    /** One driver's place in one round: {@code points} is the place, 0 before the round is ranked. */
    private record RoundScore(int points, int laps, long totalTimeMs, long bestLapMs) {}

    /** Total laps over all the class's finished heats, most first, then best lap; the order before low points. */
    private List<QualifyingResult> totalLaps(Long eventClassId) {
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
     * @param lapsCompleted total laps across all heats (the counting rounds, when ranked by points)
     * @param points        the low-points total when ranked round by round, else null
     * @param rounds        the driver's place in each finished round, in round order, when ranked round by round
     *                      (empty otherwise)
     * @param heat          the heat number the driver raced in, for a class whose heats are separate overall races
     *                      (Shikabane); null when the whole class is ranked together
     */
    public record QualifyingResult(Long entryId, long bestLapMs, int lapsCompleted, Integer points,
                                   List<RoundPlace> rounds, Integer heat) {

        /** A result ranked in one table for the whole class, with no heat. */
        public QualifyingResult(Long entryId, long bestLapMs, int lapsCompleted, Integer points,
                                List<RoundPlace> rounds) {
            this(entryId, bestLapMs, lapsCompleted, points, rounds, null);
        }

        /** A result ranked by laps, which has no points. */
        public QualifyingResult(Long entryId, long bestLapMs, int lapsCompleted) {
            this(entryId, bestLapMs, lapsCompleted, null, List.of());
        }

        /** A result with a points total and no per-round breakdown. */
        public QualifyingResult(Long entryId, long bestLapMs, int lapsCompleted, Integer points) {
            this(entryId, bestLapMs, lapsCompleted, points, List.of());
        }

        /** Whether the driver set a timed lap in any heat. */
        public boolean hasBestLap() {
            return bestLapMs != NO_LAP;
        }

        QualifyingResult plus(QualifyingResult other) {
            return new QualifyingResult(entryId, Math.min(bestLapMs, other.bestLapMs),
                    lapsCompleted + other.lapsCompleted, null, List.of());
        }
    }
}
