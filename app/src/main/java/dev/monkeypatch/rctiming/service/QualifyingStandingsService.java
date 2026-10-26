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
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
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
        Optional<RoundRules> rules = roundByRoundRules(eventClassId);
        if (rules.isPresent()) {
            List<QualifyingResult> byPoints =
                    roundByRound(eventClassId, rules.get(), lostBestRounds(eventClassId));
            if (!byPoints.isEmpty()) {
                return byPoints;
            }
        }
        // Not ranked by points, or no round has finished yet: order by laps so the table is never blank mid-round
        return totalLaps(eventClassId);
    }

    /**
     * The low-points order with one heat counted from its live positions instead of a stored result, for the live
     * top table (#480). The heat's round is scored from its finished heats plus {@code overlay}'s heat, so a round
     * that is only part run still ranks; drivers on the grid of a heat that has not run are returned as waiting,
     * and are placed behind everyone who has a result in the round. Pass {@link Overlay#without} to leave the heat
     * out instead, which gives the order before it. Empty for a class that is not ranked by
     * low points. A Shikabane class's heats are separate races, so the overlay is ignored for it. A driver who is
     * on no heat counted so far is not in the order, so a first round's waiting drivers are listed only from the
     * second round on. Under BRCA a car with no laps yet in the heat on track scores the no-show points until it
     * crosses the line.
     */
    public ProjectedResults projectedResultsFor(Long eventClassId, Overlay overlay) {
        Optional<RoundRules> rules = roundByRoundRules(eventClassId);
        if (rules.isEmpty()) {
            return new ProjectedResults(List.of(), Set.of());
        }
        Set<Long> waiting = new LinkedHashSet<>();
        List<QualifyingResult> results = roundByRound(eventClassId, rules.get(), lostBestRounds(eventClassId),
                overlay, waiting);
        return new ProjectedResults(results, waiting);
    }

    /**
     * One driver's place in a single round: laps, then total time, over every heat of the round counted so far.
     * {@code totalTimeMs} is {@link Long#MAX_VALUE} and {@code bestLapMs} too for a driver with no timed lap.
     */
    public record RoundStanding(Long entryId, int lapsCompleted, long totalTimeMs, long bestLapMs) {}

    /**
     * The order inside one round, best first, for the round section of the live top board: the round's finished
     * heats plus the {@code overlay}'s heat counted as {@link #projectedResultsFor} counts it. Drivers whose heat in
     * the round has not run are not in it yet. Empty for a class that is not ranked by low points, for a Shikabane
     * class (its heats are separate races), and when nothing in the round is counted.
     *
     * @param roundId the round holding the overlay's heat
     */
    public List<RoundStanding> projectedRoundFor(Long eventClassId, Long roundId, Overlay overlay) {
        Optional<RoundRules> rules = roundByRoundRules(eventClassId);
        if (rules.isEmpty()) {
            return List.of();
        }
        ScoredRounds scored = scoreRounds(eventClassId, rules.get(), overlay, new HashSet<>());
        int index = scored.roundIds().indexOf(roundId);
        if (scored.shikabane() || index < 0) {
            return List.of();
        }
        return scored.rounds().get(index).entrySet().stream()
                .map(e -> new RoundStanding(e.getKey(), e.getValue().laps(), e.getValue().totalTimeMs(),
                        e.getValue().bestLapMs()))
                .toList();
    }

    /**
     * One heat of a round counted in a different way for the live top table. With {@code include} false the heat
     * is left out, which gives the standings from before it. With {@code include} and {@code liveRows} it is counted
     * from the live positions of the heat on track; with {@code include} and null rows, from its stored result.
     */
    public record Overlay(long raceId, boolean include, List<LiveTimingRowDto> liveRows) {

        /** The standings as they were before this heat. */
        public static Overlay without(long raceId) {
            return new Overlay(raceId, false, null);
        }

        /** The standings with this heat counted from its live positions. */
        public static Overlay live(long raceId, List<LiveTimingRowDto> liveRows) {
            return new Overlay(raceId, true, liveRows);
        }

        /** The standings with this finished heat counted from its stored result. */
        public static Overlay stored(long raceId) {
            return new Overlay(raceId, true, null);
        }
    }

    /** A projected order, and the drivers whose heat in the overlay's round has not run yet. */
    public record ProjectedResults(List<QualifyingResult> results, Set<Long> waiting) {}

    /**
     * The points each driver scored in every scored round of a round by round class, by round id and then entry id,
     * for showing a heat's points on its result. A round with a heat still to run, or whose heats were all
     * abandoned, is left out; so is a driver who was not on the round's grid. Empty for any other qualifying type, or
     * when the class's format cannot be read.
     * The points are the round's own, before any drop for "best X from Y".
     */
    public Map<Long, Map<Long, Integer>> roundPointsFor(Long eventClassId) {
        ScoredRounds scored;
        try {
            Optional<RoundRules> rules = roundByRoundRules(eventClassId);
            if (rules.isEmpty()) {
                return Map.of();
            }
            scored = scoreRounds(eventClassId, rules.get());
        } catch (RuntimeException e) {
            // Points are an extra on a result, so a class whose format cannot be read just shows none
            log.warn("Could not score the rounds of class {} for its heat results: {}", eventClassId, e.getMessage());
            return Map.of();
        }
        Map<Long, Map<Long, Integer>> points = new HashMap<>();
        for (int i = 0; i < scored.roundIds().size(); i++) {
            Map<Long, Integer> byEntry = new HashMap<>();
            scored.rounds().get(i).forEach((entryId, score) -> byEntry.put(entryId, score.points()));
            points.put(scored.roundIds().get(i), byEntry);
        }
        return points;
    }

    /**
     * How a round by round class is scored: {@code bestRounds} is the number of rounds that count (0 for all of
     * them), {@code splitTiedPlaces} gives each car its own place in a round even on identical laps and time, and
     * {@code brca} applies the BRCA rules (flat points for a driver with no laps, and its tie-break).
     */
    private record RoundRules(int bestRounds, boolean splitTiedPlaces, boolean brca) {}

    /**
     * The scoring rules when the class's format ranks qualifying round by round (low points); empty for any other
     * qualifying type, which uses the total laps order. A format whose settings cannot be read is treated as not
     * round by round, so that the table and the seeding still work.
     */
    private Optional<RoundRules> roundByRoundRules(Long eventClassId) {
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
        return Optional.of(switch (config) {
            case BumpUpConfig bumpUp -> new RoundRules(Math.max(0, bumpUp.bestHeatsCount()), false, false);
            case PointsFinalsConfig points -> new RoundRules(Math.max(0, points.bestHeatsCount()),
                    points.splitTiedPlaces(), points.brcaQualifying());
            default -> new RoundRules(0, false, false);
        });
    }

    /**
     * Low-points qualifying ("X from Y"): in each round that is fully finished, drivers are ranked by laps, then
     * total time, over all the class's heats in the round, and score their place (1 for the round's top qualifier,
     * then 2, 3 and so on; an exact tie on laps and time shares the place). A driver missing from a round scores
     * one more than the number of drivers in it. Each driver's best {@code bestRounds} rounds count (all of them
     * when 0) and the lowest total wins; a tie goes to the most laps and then the quickest lap in the counting
     * rounds. A round with a heat still to run, or whose heats were all abandoned, is left out, since its places
     * are not settled. Empty when no round qualifies.
     *
     * <p>{@code rules} can change three things: each car gets its own place even on an exact tie, a driver with no
     * laps (or missing from the round) scores the class's entries plus one, and a tie on points follows the BRCA
     * tie-break (see {@link #brcaOrder}).
     */
    private List<QualifyingResult> roundByRound(Long eventClassId, RoundRules rules, Map<Long, Integer> lostBest) {
        return roundByRound(eventClassId, rules, lostBest, null, null);
    }

    /** As above, with one heat counted from {@code overlay} and the drivers still to run added to {@code waiting}. */
    private List<QualifyingResult> roundByRound(Long eventClassId, RoundRules rules, Map<Long, Integer> lostBest,
                                                Overlay overlay, Set<Long> waiting) {
        int bestRounds = rules.bestRounds();
        ScoredRounds scoredRounds = scoreRounds(eventClassId, rules, overlay, waiting);
        List<Map<Long, RoundScore>> rounds = scoredRounds.rounds();
        List<Integer> missingPoints = scoredRounds.missingPoints();
        Map<Long, Integer> heatOf = scoredRounds.heatOf();
        boolean shikabane = scoredRounds.shikabane();

        Set<Long> drivers = new LinkedHashSet<>();
        rounds.forEach(r -> drivers.addAll(r.keySet()));
        List<QualifyingResult> results = new ArrayList<>();
        Map<Long, CountingRounds> counting = new HashMap<>();
        for (Long entryId : drivers) {
            QualifyingResult total = totalFor(entryId, rounds, missingPoints, bestRounds,
                    lostBest.getOrDefault(entryId, 0), counting);
            results.add(!shikabane ? total : new QualifyingResult(total.entryId(), total.bestLapMs(),
                    total.lapsCompleted(), total.points(), total.rounds(), heatOf.get(entryId)));
        }
        if (rules.brca()) {
            results.sort(brcaOrder(counting));
            return results;
        }
        results.sort(Comparator
                .comparingInt(QualifyingResult::points)
                .thenComparing(Comparator.comparingInt(QualifyingResult::lapsCompleted).reversed())
                .thenComparingLong(QualifyingResult::bestLapMs)
                .thenComparing(QualifyingResult::entryId));
        return results;
    }

    /** The finished rounds of a round by round class, scored (see {@link #scoreRounds}). */
    private record ScoredRounds(List<Long> roundIds, List<Map<Long, RoundScore>> rounds, List<Integer> missingPoints,
                                Map<Long, Integer> heatOf, boolean shikabane) {}

    /**
     * Scores every round of the class that is fully finished and not wholly abandoned, in run order; the shared
     * first half of {@link #roundByRound} and {@link #roundPointsFor}.
     */
    private ScoredRounds scoreRounds(Long eventClassId, RoundRules rules) {
        return scoreRounds(eventClassId, rules, null, null);
    }

    /**
     * With an {@code overlay}, the round holding its heat is scored from that round's finished heats plus the
     * overlay's heat (left out unless it is included), whether or not the rest of the round has run; the drivers
     * on the grid of a heat left unrun are added to {@code waiting}. Every other round, and a Shikabane class, is
     * scored as usual.
     */
    private ScoredRounds scoreRounds(Long eventClassId, RoundRules rules, Overlay overlay, Set<Long> waiting) {
        Set<Long> withdrawn = entryRepository.findByEventClassIdAndStatus(eventClassId, EntryStatus.WITHDRAWN)
                .stream().map(Entry::getId).collect(Collectors.toSet());
        Map<Long, List<Race>> heatsByRound = new LinkedHashMap<>();
        for (Race race : raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER)) {
            heatsByRound.computeIfAbsent(race.getRoundId(), id -> new ArrayList<>()).add(race);
        }

        // The BRCA score for a driver who did not run: every registered entry of the class, plus one
        int dnsPoints = rules.brca()
                ? entryRepository.findByEventClassIdAndStatus(eventClassId, EntryStatus.CONFIRMED).size() + 1 : 0;
        ShikabaneConfig shikabane = eventClassRepository.findById(eventClassId)
                .flatMap(raceFormatService::shikabaneConfig).orElse(null);
        // Shikabane heats are separate overall races: a driver is ranked only against the others in their heat
        Map<Long, Integer> heatOf = new HashMap<>();
        List<Long> roundIds = new ArrayList<>();
        List<Map<Long, RoundScore>> rounds = new ArrayList<>();
        List<Integer> missingPoints = new ArrayList<>();
        for (Map.Entry<Long, List<Race>> inRound : heatsByRound.entrySet()) {
            List<Race> heats = inRound.getValue();
            Long liveRaceId = null;
            boolean overlaid = false;
            if (overlay != null && shikabane == null
                    && heats.stream().anyMatch(h -> h.getId().equals(overlay.raceId()))) {
                // Count the finished heats and the heat on track; the others have not run, so those drivers wait
                List<Race> counted = heats.stream()
                        .filter(h -> h.getStatus() == RaceStatus.FINISHED && !h.getId().equals(overlay.raceId())
                                || h.getId().equals(overlay.raceId()) && overlay.include())
                        .toList();
                if (waiting != null) {
                    for (Race heat : heats) {
                        if (!counted.contains(heat) && !heat.getId().equals(overlay.raceId())) {
                            raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())
                                    .forEach(e -> waiting.add(e.getEntryId()));
                        }
                    }
                }
                heats = counted;
                overlaid = true;
                liveRaceId = overlay.include() && overlay.liveRows() != null ? overlay.raceId() : null;
                if (heats.isEmpty()) {
                    continue;
                }
            }
            boolean finished = overlaid
                    || heats.stream().allMatch(h -> h.getStatus() == RaceStatus.FINISHED);
            boolean anyRun = heats.stream().anyMatch(h -> h.getAbandonedAt() == null);
            if (!finished || !anyRun) {
                continue;
            }
            Map<Long, RoundScore> scored = shikabane != null
                    ? scoreShikabaneRound(heats, withdrawn, shikabane)
                    : scoreRound(heats, withdrawn, rules.splitTiedPlaces(), dnsPoints,
                            liveRaceId == null ? null : overlay);
            if (shikabane != null) {
                for (Race heat : heats) {
                    raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())
                            .forEach(e -> heatOf.putIfAbsent(e.getEntryId(), heat.getHeatNumber()));
                }
            }
            roundIds.add(inRound.getKey());
            rounds.add(scored);
            missingPoints.add(rules.brca() ? Math.max(dnsPoints, scored.size() + 1) : scored.size() + 1);
        }
        return new ScoredRounds(roundIds, rounds, missingPoints, heatOf, shikabane != null);
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

    /**
     * Each driver's laps, time and place in one finished round, withdrawn entries left out. A heat that is the
     * {@code live} overlay's is scored from its live rows instead of a stored result.
     */
    private Map<Long, RoundScore> scoreRound(List<Race> heats, Set<Long> withdrawn, boolean splitTiedPlaces,
                                             int dnsPoints, Overlay live) {
        Map<Long, RoundScore> inRound = new LinkedHashMap<>();
        for (Race heat : heats) {
            // A driver who never crossed the line, or whose heat was abandoned, is last in the round. They sort
            // behind a driver with a zero-lap result row only through the entry id, since both have no laps.
            for (RaceEntry onGrid : raceEntryRepository.findByRaceIdOrderByGridPosition(heat.getId())) {
                inRound.putIfAbsent(onGrid.getEntryId(), new RoundScore(0, 0, Long.MAX_VALUE, NO_LAP));
            }
            if (live != null && heat.getId().equals(live.raceId())) {
                for (LiveTimingRowDto row : live.liveRows()) {
                    inRound.put(row.entryId(), new RoundScore(0, row.lapsCompleted(),
                            row.totalTimeMs() == null ? Long.MAX_VALUE : row.totalTimeMs(),
                            row.bestLapMs() == null ? NO_LAP : row.bestLapMs()));
                }
            } else if (heat.getAbandonedAt() == null) {
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
            boolean tied = !splitTiedPlaces && previous != null && previous.laps() == raw.laps()
                    && previous.totalTimeMs() == raw.totalTimeMs();
            if (!tied) {
                place = i + 1;
            }
            previous = raw;
            // BRCA: a driver with no laps did not run, and scores the flat penalty rather than a place
            int points = dnsPoints > 0 && raw.laps() == 0 ? dnsPoints : place;
            scored.put(entry.getKey(), new RoundScore(points, raw.laps(), raw.totalTimeMs(), raw.bestLapMs()));
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
                                             List<Integer> missingPoints, int bestRounds, int lostBest,
                                             Map<Long, CountingRounds> countingByEntry) {
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
        countingByEntry.put(entryId, new CountingRounds(
                counting.stream().map(RoundScore::points).sorted().toList(),
                counting.stream().mapToLong(r -> Math.min(r.totalTimeMs(), MAX_ROUND_TIME_MS)).sum()));
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

    /** A round's time for a driver who has none is counted as this long, so summing rounds cannot overflow. */
    private static final long MAX_ROUND_TIME_MS = 24L * 60 * 60 * 1000;

    /**
     * What the BRCA tie-break reads of a driver's counting rounds: their points, best first, and their times added
     * together (the laps are already in the driver's result).
     */
    record CountingRounds(List<Integer> pointsBestFirst, long totalTimeMs) {}

    /**
     * The BRCA qualifying order: lowest total points, then the best single counting round, then the second best
     * (comparing the counting rounds' points best first, so a 1 + 3 beats a 2 + 2), then the most laps in the
     * counting rounds, then the least time in them, then the quickest lap and the entry id so that the order never
     * changes between calls.
     */
    static Comparator<QualifyingResult> brcaOrder(Map<Long, CountingRounds> counting) {
        Comparator<QualifyingResult> byRounds = (a, b) -> {
            List<Integer> left = counting.get(a.entryId()).pointsBestFirst();
            List<Integer> right = counting.get(b.entryId()).pointsBestFirst();
            for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
                int compared = Integer.compare(left.get(i), right.get(i));
                if (compared != 0) {
                    return compared;
                }
            }
            return 0;
        };
        return Comparator.comparingInt(QualifyingResult::points)
                .thenComparing(byRounds)
                .thenComparing(Comparator.comparingInt(QualifyingResult::lapsCompleted).reversed())
                .thenComparingLong(r -> counting.get(r.entryId()).totalTimeMs())
                .thenComparingLong(QualifyingResult::bestLapMs)
                .thenComparing(QualifyingResult::entryId);
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
