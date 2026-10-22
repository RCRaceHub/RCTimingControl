package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.service.FinalLegScoring;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipClasses.CHAMPIONSHIP_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipExclusions.CHAMPIONSHIP_EXCLUSIONS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipPointsScale.CHAMPIONSHIP_POINTS_SCALE;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Championships.CHAMPIONSHIPS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Competitors.COMPETITORS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Entries.ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Events.EVENTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RaceEntries.RACE_ENTRIES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * Championship standings (CHAMP-01, CHAMP-02, CHAMP-04, CHAMP-07, CHAMP-08, CHAMP-09), worked out on demand
 * from the result snapshots of the linked events. Each event linked to the championship is a round.
 *
 * <p>A driver scores, per class, the points for their best position at each round. Scoring from both qualifying and
 * finals (CHAMP-06) pays the points of the best qualifying position and of the best final position and sums them;
 * the round's position is the better of the two. A driver on the grid at a round who has no result in it has a DNS
 * round worth nothing, which still counts towards Y. A round of finals is placed as one: the A final's order, then
 * the B final's without those who bumped up, and so on, so a B final win is not a first place. The TQ and A-final
 * winner bonuses are per driver, per class and per round: winning in two classes, or two rounds, earns each. The TQ
 * goes to one driver per class and round, the one at the top of the class's qualifying table once all its qualifying
 * heats are finished (the order finals are seeded in, so lowest points for a low-points class), not to every heat
 * winner. Both come from every finished race, whatever the scoring source. A round a driver is excluded from earns
 * them nothing, bonuses included, and a TQ they top is not passed down.
 * The worst rounds are then dropped to the best X from Y, and each class is ranked.
 *
 * <p>Drivers are competitors (L5, #13): entries map to {@code entries.competitor_id}, so a driver with no login
 * still scores, and exclusions are keyed by competitor id. Reads only through the jOOQ DSL, with no repositories.
 */
@Service
@ReadTransaction
public class ChampionshipStandingsQuery {

    private static final Logger log = LoggerFactory.getLogger(ChampionshipStandingsQuery.class);

    private final DSLContext dsl;
    private final ResultSnapshotJson snapshotJson;
    private final QualifyingStandingsService qualifyingStandings;
    private final RaceFormatService raceFormatService;
    private final EventClassRepository eventClassRepository;

    public ChampionshipStandingsQuery(DSLContext dsl, ResultSnapshotJson snapshotJson,
                                      QualifyingStandingsService qualifyingStandings,
                                      RaceFormatService raceFormatService,
                                      EventClassRepository eventClassRepository) {
        this.dsl = dsl;
        this.snapshotJson = snapshotJson;
        this.qualifyingStandings = qualifyingStandings;
        this.raceFormatService = raceFormatService;
        this.eventClassRepository = eventClassRepository;
    }

    /**
     * Whether an event class is run to the Shikabane format. Shikabane is for fun days, so its races never score
     * in a championship, whatever the championship's scoring source.
     */
    private boolean isShikabane(Long eventClassId) {
        return raceFormatService.shikabaneConfig(eventClassRepository.findById(eventClassId).orElse(null))
                .isPresent();
    }

    /**
     * The standings of every class in the championship, computed afresh from the stored results (nothing is saved).
     * Rows come grouped by racing class in class id order, and within a class by total points highest first, then
     * by display name; the position is the row's place in its class, so equal points are not given a shared rank
     * here. A driver appears once per class they scored or were on a grid in. Runs in one read snapshot, so the
     * rounds, rules and results agree with each other.
     *
     * @throws EntityNotFoundException if there is no such championship
     */
    public List<StandingsRowDto> computeStandings(Long championshipId) {
        Rules rules = rules(championshipId);
        List<ChampionshipRound> rounds = rounds(championshipId);
        Map<Long, EventResults> resultsByEvent = results(rounds, rules.scoringSource());

        Tally tally = new Tally();
        for (ChampionshipRound round : rounds) {
            score(round, resultsByEvent.getOrDefault(round.eventId(), EventResults.NONE), rules, tally);
        }
        return rank(rows(tally, rules));
    }

    /** How the championship scores: everything but the results themselves. */
    private record Rules(String scoringSource, BestXFromY bestXFromY, Map<Long, BestXFromY> classBestXFromY,
                         Map<Integer, Integer> pointsScale, int tqBonus, int aFinalBonus,
                         Set<DriverEvent> exclusions) {

        int points(int position) {
            return pointsScale.getOrDefault(position, 0);
        }

        boolean excluded(Long driverId, Long eventId) {
            return exclusions.contains(new DriverEvent(driverId, eventId));
        }

        /**
         * Whether the class scores in the championship: only the classes added to it do, and one with no classes
         * added yet scores every class that has results.
         */
        boolean scores(Long racingClassId) {
            return classBestXFromY.isEmpty() || classBestXFromY.containsKey(racingClassId);
        }

        /** The class's own best X from Y, either half falling back to the championship's. */
        BestXFromY bestXFromY(Long racingClassId) {
            BestXFromY own = classBestXFromY.get(racingClassId);
            if (own == null) {
                return bestXFromY;
            }
            return new BestXFromY(own.x() != null ? own.x() : bestXFromY.x(),
                    own.y() != null ? own.y() : bestXFromY.y());
        }
    }

    /** Best X rounds from Y; either is null when not set. */
    private record BestXFromY(Integer x, Integer y) {}

    /** A driver at one event, the key exclusions are kept by. */
    private record DriverEvent(Long driverId, Long eventId) {}

    /** Loads the championship's scoring settings, per-class overrides, points scale and exclusions. */
    private Rules rules(Long championshipId) {
        var championship = dsl
                .select(CHAMPIONSHIPS.BEST_X_FROM_Y_X, CHAMPIONSHIPS.BEST_X_FROM_Y_Y, CHAMPIONSHIPS.SCORING_SOURCE,
                        CHAMPIONSHIPS.TQ_BONUS_POINTS, CHAMPIONSHIPS.AFINAL_WINNER_BONUS_POINTS)
                .from(CHAMPIONSHIPS)
                .where(CHAMPIONSHIPS.ID.eq(championshipId))
                .fetchOne();
        if (championship == null) {
            throw new EntityNotFoundException("Championship not found: " + championshipId);
        }

        Map<Long, BestXFromY> classBestXFromY = new HashMap<>();
        dsl.select(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID, CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_X,
                        CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_Y)
                .from(CHAMPIONSHIP_CLASSES)
                .where(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId))
                .forEach(r -> classBestXFromY.put(r.get(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID),
                        new BestXFromY(r.get(CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_X),
                                r.get(CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_Y))));

        Map<Integer, Integer> pointsScale = new HashMap<>();
        dsl.select(CHAMPIONSHIP_POINTS_SCALE.POSITION, CHAMPIONSHIP_POINTS_SCALE.POINTS)
                .from(CHAMPIONSHIP_POINTS_SCALE)
                .where(CHAMPIONSHIP_POINTS_SCALE.CHAMPIONSHIP_ID.eq(championshipId))
                .forEach(r -> pointsScale.put(r.get(CHAMPIONSHIP_POINTS_SCALE.POSITION),
                        r.get(CHAMPIONSHIP_POINTS_SCALE.POINTS)));

        Set<DriverEvent> exclusions = new HashSet<>();
        dsl.select(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID, CHAMPIONSHIP_EXCLUSIONS.EVENT_ID)
                .from(CHAMPIONSHIP_EXCLUSIONS)
                .where(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID.eq(championshipId))
                .forEach(r -> exclusions.add(new DriverEvent(r.get(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID),
                        r.get(CHAMPIONSHIP_EXCLUSIONS.EVENT_ID))));

        return new Rules(championship.get(CHAMPIONSHIPS.SCORING_SOURCE),
                new BestXFromY(championship.get(CHAMPIONSHIPS.BEST_X_FROM_Y_X),
                        championship.get(CHAMPIONSHIPS.BEST_X_FROM_Y_Y)),
                classBestXFromY, pointsScale,
                championship.get(CHAMPIONSHIPS.TQ_BONUS_POINTS),
                championship.get(CHAMPIONSHIPS.AFINAL_WINNER_BONUS_POINTS),
                exclusions);
    }

    /** An event linked to the championship. */
    private record ChampionshipRound(Long eventId, int roundNumber, String eventName) {}

    /** A finished race at a linked event, with its result rows best first. */
    private record FinishedRace(Long raceId, Long roundId, Long eventId, String roundType, String finalLetter,
                                Long racingClassId, boolean scores, boolean abandoned,
                                List<ResultSnapshotDto.ResultRow> positions, Set<Long> grid) {}

    /** The finals of one class in one round of finals, which are placed together. */
    private record RoundClass(Long roundId, Long racingClassId) {}

    /** The competitor and racing class of an entry on the grid of a finished race. */
    private record EntryRef(Long driverId, Long racingClassId) {}

    /**
     * One event's finished races, and the entries on their grids by entry id: those of the races that score, and
     * those of every finished race, which the bonuses are paid from.
     */
    private record EventResults(List<FinishedRace> races, Map<Long, EntryRef> scoringEntries,
                                Map<Long, EntryRef> allEntries, Map<Long, String> displayNames,
                                Map<Long, Long> topQualifiers, Map<RoundClass, String> firstOpenFinal) {

        static final EventResults NONE =
                new EventResults(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** The linked events in round number order. */
    private List<ChampionshipRound> rounds(Long championshipId) {
        return dsl.select(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID, CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER, EVENTS.NAME)
                .from(CHAMPIONSHIP_EVENT_LINKS)
                .join(EVENTS).on(EVENTS.ID.eq(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID))
                .where(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId))
                .orderBy(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER.asc())
                .fetch(r -> new ChampionshipRound(r.get(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID),
                        r.get(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER), r.get(EVENTS.NAME)));
    }

    /**
     * The finished races of every linked event and the entries on their grids, in two queries for the whole
     * championship. Each race's snapshot is read once, for both the points and the bonuses.
     */
    private Map<Long, EventResults> results(List<ChampionshipRound> rounds, String scoringSource) {
        if (rounds.isEmpty()) {
            return Map.of();
        }
        List<Long> eventIds = rounds.stream().map(ChampionshipRound::eventId).toList();

        Map<Long, FinishedRace> races = new HashMap<>();
        Map<Long, EventResults> results = new HashMap<>();
        Map<Long, Boolean> shikabaneClasses = new HashMap<>();
        dsl.select(RACES.ID, RACES.ROUND_ID, RACES.EVENT_CLASS_ID, RACES.FINAL_LETTER, RACES.ABANDONED_AT, ROUNDS.TYPE, ROUNDS.EVENT_ID,
                        EVENT_CLASSES.RACING_CLASS_ID,
                        RESULT_SNAPSHOTS.POSITIONS_JSON)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .leftJoin(RESULT_SNAPSHOTS).on(RESULT_SNAPSHOTS.RACE_ID.eq(RACES.ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(RACES.STATUS.eq("FINISHED"))
                .forEach(r -> {
                    Long raceId = r.get(RACES.ID);
                    String roundType = r.get(ROUNDS.TYPE);
                    FinishedRace race = new FinishedRace(raceId, r.get(RACES.ROUND_ID), r.get(ROUNDS.EVENT_ID),
                            roundType, r.get(RACES.FINAL_LETTER),
                            r.get(EVENT_CLASSES.RACING_CLASS_ID),
                            // A Shikabane zombie round is a last chance for a place in A, not a final of its own
                            scores(scoringSource, roundType) && !"Z".equals(r.get(RACES.FINAL_LETTER))
                                    && !shikabaneClasses.computeIfAbsent(r.get(RACES.EVENT_CLASS_ID),
                                            this::isShikabane),
                            r.get(RACES.ABANDONED_AT) != null,
                            snapshotJson.positions(raceId, r.get(RESULT_SNAPSHOTS.POSITIONS_JSON)), new HashSet<>());
                    races.put(raceId, race);
                    results.computeIfAbsent(race.eventId(), k -> new EventResults(new ArrayList<>(),
                            new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                            new HashMap<>())).races().add(race);
                });

        // The entry's own class, which can differ from the race's, decides which class a DNS is in
        dsl.select(RACE_ENTRIES.RACE_ID, RACE_ENTRIES.ENTRY_ID, ENTRIES.COMPETITOR_ID, EVENT_CLASSES.RACING_CLASS_ID,
                        COMPETITORS.DISPLAY_NAME)
                .from(RACE_ENTRIES)
                .join(RACES).on(RACES.ID.eq(RACE_ENTRIES.RACE_ID))
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                .leftJoin(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(ENTRIES.EVENT_CLASS_ID))
                .leftJoin(COMPETITORS).on(COMPETITORS.ID.eq(ENTRIES.COMPETITOR_ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(RACES.STATUS.eq("FINISHED"))
                .forEach(r -> {
                    FinishedRace race = races.get(r.get(RACE_ENTRIES.RACE_ID));
                    if (race == null) {
                        return; // a race with no event class, which the first query leaves out
                    }
                    EventResults event = results.get(race.eventId());
                    Long entryId = r.get(RACE_ENTRIES.ENTRY_ID);
                    EntryRef entry = new EntryRef(r.get(ENTRIES.COMPETITOR_ID), r.get(EVENT_CLASSES.RACING_CLASS_ID));
                    event.allEntries().put(entryId, entry);
                    race.grid().add(entryId);
                    if (race.scores()) {
                        event.scoringEntries().put(entryId, entry);
                    }
                    String displayName = r.get(COMPETITORS.DISPLAY_NAME);
                    if (entry.driverId() != null && displayName != null) {
                        event.displayNames().put(entry.driverId(), displayName);
                    }
                });
        combineFinalLegs(eventIds, results);
        loadTopQualifiers(eventIds, results);
        loadOpenFinals(eventIds, results);
        return results;
    }

    /** The races that are the legs of one A final: its round and class. */
    private record LegGroup(Long roundId, Long eventClassId) {}

    /** One leg of an A final, whether or not it has finished. */
    private record Leg(Long raceId, int heatNumber, Long eventId) {}

    /**
     * Replaces the finished legs of an A final run in legs (#427) with one race that carries the combined result, so
     * the final is placed, and its winner bonus paid, from the combined order. Until every leg has finished the legs
     * are left out altogether, so the A final is not placed from the legs run so far; the finals below it wait for it
     * as for any final that is still to run. The combined race takes the first leg's id.
     */
    private void combineFinalLegs(List<Long> eventIds, Map<Long, EventResults> results) {
        Map<LegGroup, List<Leg>> groups = new LinkedHashMap<>();
        dsl.select(RACES.ID, RACES.ROUND_ID, RACES.EVENT_CLASS_ID, RACES.HEAT_NUMBER, ROUNDS.EVENT_ID)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(ROUNDS.TYPE.eq("FINAL"))
                .and(RACES.FINAL_LETTER.eq("A"))
                .orderBy(RACES.HEAT_NUMBER.asc())
                .forEach(r -> groups.computeIfAbsent(
                                new LegGroup(r.get(RACES.ROUND_ID), r.get(RACES.EVENT_CLASS_ID)), k -> new ArrayList<>())
                        .add(new Leg(r.get(RACES.ID), r.get(RACES.HEAT_NUMBER), r.get(ROUNDS.EVENT_ID))));
        groups.forEach((group, legs) -> {
            EventResults event = legs.size() < 2 ? null : results.get(legs.get(0).eventId());
            if (event == null) {
                return;
            }
            Set<Long> legIds = new HashSet<>();
            legs.forEach(leg -> legIds.add(leg.raceId()));
            Map<Long, Integer> heatOf = new HashMap<>();
            legs.forEach(leg -> heatOf.put(leg.raceId(), leg.heatNumber()));
            List<FinishedRace> finished = event.races().stream().filter(r -> legIds.contains(r.raceId()))
                    .sorted(Comparator.comparingInt((FinishedRace r) -> heatOf.get(r.raceId())))
                    .toList();
            event.races().removeAll(finished);
            if (finished.size() == legs.size()) {
                event.races().add(combinedLegs(finished, group.eventClassId()));
            }
        });
    }

    /** The combined result of an A final's finished legs as one race, best first (see {@link FinalLegScoring}). */
    private FinishedRace combinedLegs(List<FinishedRace> legs, Long eventClassId) {
        FinishedRace first = legs.get(0);
        Set<Long> drivers = new LinkedHashSet<>(first.grid());
        Map<Long, ResultSnapshotDto.ResultRow> sample = new HashMap<>();
        List<Map<Long, FinalLegScoring.Run>> runs = new ArrayList<>();
        for (FinishedRace leg : legs) {
            // An abandoned leg is over but has no result: it is left out of the scoring
            if (leg.abandoned()) {
                continue;
            }
            Map<Long, FinalLegScoring.Run> legRuns = new LinkedHashMap<>();
            for (ResultSnapshotDto.ResultRow row : leg.positions()) {
                legRuns.put(row.entryId(), new FinalLegScoring.Run(row.lapsCompleted(), row.totalTimeMs()));
                sample.putIfAbsent(row.entryId(), row);
                drivers.add(row.entryId());
            }
            runs.add(legRuns);
        }
        int legsCounted = eventClassRepository.findById(eventClassId).map(raceFormatService::legsCounted).orElse(0);
        List<ResultSnapshotDto.ResultRow> rows = new ArrayList<>();
        for (FinalLegScoring.Standing standing : FinalLegScoring.rank(runs, drivers, legsCounted)) {
            ResultSnapshotDto.ResultRow row = sample.get(standing.entryId());
            rows.add(new ResultSnapshotDto.ResultRow(rows.size() + 1, standing.entryId(),
                    row == null ? null : row.competitorId(), row == null ? "" : row.driverName(),
                    row == null ? "" : row.carNumber(), standing.laps(), standing.timeMs(), null, null));
        }
        return new FinishedRace(first.raceId(), first.roundId(), first.eventId(), first.roundType(),
                first.finalLetter(), first.racingClassId(), first.scores(), false, rows, first.grid());
    }

    /**
     * For each round of finals still to be finished, its first unfinished final by letter: the finals below it are
     * not placed until it is done, since a driver may yet bump up out of them.
     */
    private void loadOpenFinals(List<Long> eventIds, Map<Long, EventResults> results) {
        dsl.select(ROUNDS.EVENT_ID, RACES.ROUND_ID, EVENT_CLASSES.RACING_CLASS_ID, RACES.FINAL_LETTER)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(ROUNDS.TYPE.eq("FINAL"))
                .and(RACES.STATUS.ne("FINISHED"))
                .forEach(r -> {
                    EventResults event = results.get(r.get(ROUNDS.EVENT_ID));
                    String letter = r.get(RACES.FINAL_LETTER);
                    if (event != null && letter != null) {
                        event.firstOpenFinal().merge(
                                new RoundClass(r.get(RACES.ROUND_ID), r.get(EVENT_CLASSES.RACING_CLASS_ID)), letter,
                                (a, b) -> a.compareTo(b) <= 0 ? a : b);
                    }
                });
    }

    /**
     * The entry that topped each class's qualifying once it was complete: the first in the table the finals are
     * seeded from, which for a low-points class is the lowest score, not the most laps. A class with a qualifying
     * heat still to run has no TQ yet, and nor does one whose heats were all abandoned.
     */
    private void loadTopQualifiers(List<Long> eventIds, Map<Long, EventResults> results) {
        Map<Long, List<QualifyingHeat>> heatsByClass = new HashMap<>();
        dsl.select(RACES.EVENT_CLASS_ID, ROUNDS.EVENT_ID, EVENT_CLASSES.RACING_CLASS_ID, RACES.STATUS,
                        RACES.ABANDONED_AT)
                .from(RACES)
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .where(ROUNDS.EVENT_ID.in(eventIds))
                .and(ROUNDS.TYPE.eq("QUALIFIER"))
                .forEach(r -> heatsByClass.computeIfAbsent(r.get(RACES.EVENT_CLASS_ID), k -> new ArrayList<>())
                        .add(new QualifyingHeat(r.get(ROUNDS.EVENT_ID), r.get(EVENT_CLASSES.RACING_CLASS_ID),
                                "FINISHED".equals(r.get(RACES.STATUS)), r.get(RACES.ABANDONED_AT) != null)));
        heatsByClass.forEach((eventClassId, heats) -> {
            if (isShikabane(eventClassId)) {
                return; // no top qualifier bonus for a fun-day format
            }
            boolean complete = heats.stream().allMatch(QualifyingHeat::finished)
                    && heats.stream().anyMatch(h -> !h.abandoned());
            EventResults event = results.get(heats.get(0).eventId());
            if (!complete || event == null) {
                return;
            }
            try {
                qualifyingStandings.rankedResultsFor(eventClassId).stream()
                        .findFirst()
                        .filter(top -> top.lapsCompleted() > 0)
                        .ifPresent(top -> event.topQualifiers().put(heats.get(0).racingClassId(), top.entryId()));
            } catch (RuntimeException e) {
                log.warn("No TQ for event class {}: its qualifying could not be ranked: {}", eventClassId,
                        e.getMessage());
            }
        });
    }

    /** A qualifying heat of an event class, for working out whether its qualifying is complete. */
    private record QualifyingHeat(Long eventId, Long racingClassId, boolean finished, boolean abandoned) {}

    /** Whether a race in a round of this type counts for points. */
    private static boolean scores(String scoringSource, String roundType) {
        if ("QUALIFYING".equals(scoringSource)) {
            return "QUALIFIER".equals(roundType);
        }
        if ("FINALS".equals(scoringSource)) {
            return "FINAL".equals(roundType);
        }
        return "QUALIFIER".equals(roundType) || "FINAL".equals(roundType); // BOTH; practice never scores
    }

    /** A driver in one racing class: one row of the standings. */
    private record DriverClass(Long driverId, Long racingClassId) {}

    /**
     * A driver's best qualifying and best final position in one class at one event; either is null when they had no
     * result of that kind.
     */
    private record SourcePositions(Integer qualifying, Integer finals) {

        static SourcePositions of(String roundType, int position) {
            return "QUALIFIER".equals(roundType)
                    ? new SourcePositions(position, null)
                    : new SourcePositions(null, position);
        }

        SourcePositions better(SourcePositions other) {
            return new SourcePositions(min(qualifying, other.qualifying), min(finals, other.finals));
        }

        /** The better of the positions the driver has. */
        int best() {
            return min(qualifying, finals);
        }

        /** The points of each position the driver has, summed (CHAMP-06). */
        int points(Rules rules) {
            return (qualifying != null ? rules.points(qualifying) : 0) + (finals != null ? rules.points(finals) : 0);
        }

        private static Integer min(Integer a, Integer b) {
            if (a == null) {
                return b;
            }
            return b == null ? a : Integer.valueOf(Math.min(a, b));
        }
    }

    /** A driver winning something in one class at one event (a round of the championship). */
    private record ClassRound(DriverClass driverClass, Long eventId) {}

    /** What the rounds add up to, before drops and ranking. */
    private static final class Tally {
        final Map<DriverClass, List<RoundResultDto>> rounds = new LinkedHashMap<>();
        final Map<Long, String> displayNames = new HashMap<>();
        final Set<ClassRound> tqWins = new HashSet<>();
        final Set<ClassRound> aFinalWins = new HashSet<>();

        void add(DriverClass driverClass, RoundResultDto round) {
            rounds.computeIfAbsent(driverClass, k -> new ArrayList<>()).add(round);
        }
    }

    /**
     * Adds one round's results to the tally: a result per driver and class who has a placed result, scored from the
     * better of their qualifying and final positions (or the sum of both points, see {@link SourcePositions}), and a
     * DNS result for each driver on a scoring grid who has none in their class. An excluded driver's round is kept,
     * flagged excluded and worth nothing.
     */
    private static void score(ChampionshipRound round, EventResults results, Rules rules, Tally tally) {
        Long eventId = round.eventId();
        tally.displayNames.putAll(results.displayNames());

        // Each driver's best qualifying and best final position in each racing class, over every race that scores
        Map<Long, Map<Long, Integer>> overallInFinals = overallFinalPositions(results.races(), results.firstOpenFinal());
        Map<Long, Map<Long, SourcePositions>> bestPositions = new HashMap<>();
        for (FinishedRace race : results.races()) {
            if (!race.scores()) {
                continue;
            }
            // A final is placed across the round (see overallFinalPositions); other races by their own places
            Map<Long, Integer> places = new LinkedHashMap<>();
            if ("FINAL".equals(race.roundType())) {
                places.putAll(overallInFinals.getOrDefault(race.raceId(), Map.of()));
            } else {
                race.positions().forEach(row -> places.put(row.entryId(), row.position()));
            }
            places.forEach((entryId, position) -> {
                EntryRef entry = results.scoringEntries().get(entryId);
                if (entry == null || entry.driverId() == null) {
                    return;
                }
                bestPositions.computeIfAbsent(race.racingClassId(), k -> new HashMap<>())
                        .merge(entry.driverId(), SourcePositions.of(race.roundType(), position),
                                SourcePositions::better);
            });
        }

        bonuses(eventId, results, rules, tally);

        bestPositions.forEach((racingClassId, positions) -> positions.forEach((driverId, position) -> {
            boolean excluded = rules.excluded(driverId, eventId);
            tally.add(new DriverClass(driverId, racingClassId), new RoundResultDto(round.roundNumber(), eventId,
                    round.eventName(), excluded ? 0 : position.best(), excluded ? 0 : position.points(rules),
                    excluded, false));
        }));

        // On a grid but in no result for their class: a DNS, which counts towards Y. A driver in several heats
        // is on several grids, and still has one DNS for the round.
        Set<DriverClass> didNotStart = new HashSet<>();
        for (EntryRef entry : results.scoringEntries().values()) {
            if (entry.driverId() == null || entry.racingClassId() == null) {
                continue;
            }
            boolean finished = bestPositions.getOrDefault(entry.racingClassId(), Map.of())
                    .containsKey(entry.driverId());
            DriverClass driverClass = new DriverClass(entry.driverId(), entry.racingClassId());
            if (!finished && didNotStart.add(driverClass)) {
                tally.add(driverClass, new RoundResultDto(round.roundNumber(), eventId, round.eventName(),
                        0, 0, rules.excluded(entry.driverId(), eventId), false));
            }
        }
    }

    /**
     * Each driver's place across a round of finals, by race and entry: the A final's order, then the B final's
     * without those who bumped up into the A, and so on down the letters, so that the winner of the B final is
     * placed after everyone in the A. A final's own places are kept, so a lone finisher's 4th stays 4th. A driver who
     * raced two finals of the round has the place from the higher one only, and one who bumped up and then did not
     * start it is classed last in the higher final. Rounds and classes are kept apart; a race with no letter comes
     * first. A final is not placed until every final above it has finished.
     */
    private static Map<Long, Map<Long, Integer>> overallFinalPositions(List<FinishedRace> races,
                                                                       Map<RoundClass, String> firstOpenFinal) {
        Map<RoundClass, List<FinishedRace>> finalsByRound = new LinkedHashMap<>();
        for (FinishedRace race : races) {
            if ("FINAL".equals(race.roundType())) {
                finalsByRound.computeIfAbsent(new RoundClass(race.roundId(), race.racingClassId()),
                        k -> new ArrayList<>()).add(race);
            }
        }
        Map<Long, Map<Long, Integer>> overall = new HashMap<>();
        finalsByRound.forEach((roundClass, finals) -> {
            finals.sort(Comparator.comparing(FinishedRace::finalLetter,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            String open = firstOpenFinal.get(roundClass);
            Set<Long> placed = new HashSet<>();
            Set<Long> onHigherGrid = new HashSet<>();
            int placesAbove = 0;
            for (int i = 0; i < finals.size(); i++) {
                FinishedRace race = finals.get(i);
                Map<Long, Integer> positions = new HashMap<>();
                overall.put(race.raceId(), positions);
                if (open != null && race.finalLetter() != null && race.finalLetter().compareTo(open) > 0) {
                    continue; // a final above this one is still to run
                }
                List<ResultSnapshotDto.ResultRow> rows = new ArrayList<>(race.positions());
                rows.sort(Comparator.comparingInt(ResultSnapshotDto.ResultRow::position));
                int bumped = 0;
                for (ResultSnapshotDto.ResultRow row : rows) {
                    if (onHigherGrid.contains(row.entryId()) || !placed.add(row.entryId())) {
                        bumped++; // placed in a final above, so not here
                        continue;
                    }
                    positions.put(row.entryId(), placesAbove + row.position() - bumped);
                }
                int lastPlace = Math.max(placesAbove,
                        positions.values().stream().mapToInt(Integer::intValue).max().orElse(0));
                // A driver bumped up who did not start this final is classed last in it, in the order they
                // finished the final below. Nobody is classed if no one raced this final.
                if (!rows.isEmpty()) {
                    Set<Long> started = new HashSet<>();
                    rows.forEach(row -> started.add(row.entryId()));
                    Map<Long, Integer> belowPlace = new HashMap<>();
                    for (FinishedRace lower : finals.subList(i + 1, finals.size())) {
                        lower.positions().forEach(row -> belowPlace.putIfAbsent(row.entryId(), row.position()));
                    }
                    List<Long> noStart = new ArrayList<>();
                    for (Long entryId : race.grid()) {
                        if (!started.contains(entryId) && !onHigherGrid.contains(entryId)
                                && belowPlace.containsKey(entryId) && placed.add(entryId)) {
                            noStart.add(entryId);
                        }
                    }
                    noStart.sort((a, b) -> {
                        int byPlace = Integer.compare(belowPlace.get(a), belowPlace.get(b));
                        return byPlace != 0 ? byPlace : Long.compare(a, b);
                    });
                    for (Long entryId : noStart) {
                        positions.put(entryId, ++lastPlace);
                    }
                }
                placesAbove = lastPlace;
                if (!rows.isEmpty()) {
                    onHigherGrid.addAll(race.grid()); // a final nobody raced takes no one from the final below
                }
            }
        });
        return overall;
    }

    /** Who topped qualifying (TQ) or won the A final at the round, from every finished race. */
    private static void bonuses(Long eventId, EventResults results, Rules rules, Tally tally) {
        topQualifiers(eventId, results, rules, tally);
        for (FinishedRace race : results.races()) {
            if (!"FINAL".equals(race.roundType()) || !"A".equals(race.finalLetter())) {
                continue;
            }
            for (ResultSnapshotDto.ResultRow row : race.positions()) {
                EntryRef entry = results.allEntries().get(row.entryId());
                if (entry == null || entry.driverId() == null || row.position() != 1
                        || rules.excluded(entry.driverId(), eventId)) {
                    continue;
                }
                tally.aFinalWins.add(new ClassRound(new DriverClass(entry.driverId(), race.racingClassId()), eventId));
            }
        }
    }

    /**
     * The TQ (CHAMP-07): in each class, the entry that topped qualifying once it was complete (see
     * {@link #loadTopQualifiers}). A driver excluded from the round earns nothing, and the TQ is not passed to the
     * next car.
     */
    private static void topQualifiers(Long eventId, EventResults results, Rules rules, Tally tally) {
        results.topQualifiers().forEach((racingClassId, entryId) -> {
            EntryRef entry = results.allEntries().get(entryId);
            if (entry != null && entry.driverId() != null && !rules.excluded(entry.driverId(), eventId)) {
                tally.tqWins.add(new ClassRound(new DriverClass(entry.driverId(), racingClassId), eventId));
            }
        });
    }

    /** One unranked row per driver and class: rounds marked with their drops, and the total with the bonuses added. */
    private static List<StandingsRowDto> rows(Tally tally, Rules rules) {
        List<StandingsRowDto> rows = new ArrayList<>();
        tally.rounds.forEach((driverClass, rounds) -> {
            if (!rules.scores(driverClass.racingClassId())) {
                return;
            }
            List<RoundResultDto> marked = dropWorst(rounds, rules.bestXFromY(driverClass.racingClassId()));
            int totalPoints = marked.stream()
                    .filter(r -> !r.dropped() && !r.excluded())
                    .mapToInt(RoundResultDto::points)
                    .sum();
            totalPoints += rules.tqBonus() * roundsWon(tally.tqWins, driverClass);
            totalPoints += rules.aFinalBonus() * roundsWon(tally.aFinalWins, driverClass);
            rows.add(new StandingsRowDto(driverClass.driverId(),
                    tally.displayNames.getOrDefault(driverClass.driverId(), ""),
                    driverClass.racingClassId(), totalPoints, 0, marked));
        });
        return rows;
    }

    /**
     * The rounds in round order, with every round past the driver's best X marked dropped. A driver with X rounds
     * or fewer keeps them all, so a round not raced costs nothing beyond the points it would have scored. Y does
     * not change the result; with no X every round counts. Best means most points, then the better (lower)
     * position, so a finish worth nothing is dropped before a DNS (position 0).
     */
    private static List<RoundResultDto> dropWorst(List<RoundResultDto> rounds, BestXFromY rule) {
        int count = rounds.size();
        int dropFrom = rule.x() != null ? Math.min(Math.max(0, rule.x()), count) : count;

        List<RoundResultDto> marked = new ArrayList<>(rounds);
        marked.sort(Comparator.comparingInt(RoundResultDto::points).reversed()
                .thenComparingInt(RoundResultDto::position));
        for (int i = dropFrom; i < count; i++) {
            RoundResultDto r = marked.get(i);
            marked.set(i, new RoundResultDto(r.roundNumber(), r.eventId(), r.eventName(), r.position(), r.points(),
                    r.excluded(), true));
        }
        marked.sort(Comparator.comparingInt(RoundResultDto::roundNumber));
        return marked;
    }

    /** How many rounds the driver won in the class: each is paid its bonus once. */
    private static int roundsWon(Set<ClassRound> wins, DriverClass driverClass) {
        return (int) wins.stream().filter(w -> w.driverClass().equals(driverClass)).count();
    }

    /**
     * Each class by points, then by countback (see {@link #countbackCompare}), then display name. Drivers level on
     * points and countback share a position and the next one skips ahead (1, 1, 3). Classes come out in racing class
     * order (a class with no racing class last): the order rows are first seen in follows hash-map order, which
     * changes with the ids.
     */
    private static List<StandingsRowDto> rank(List<StandingsRowDto> rows) {
        Map<Long, List<StandingsRowDto>> byClass = new LinkedHashMap<>();
        for (StandingsRowDto row : rows) {
            byClass.computeIfAbsent(row.racingClassId(), k -> new ArrayList<>()).add(row);
        }
        Map<Long, List<StandingsRowDto>> inClassOrder =
                new TreeMap<>(Comparator.nullsLast(Comparator.<Long>naturalOrder()));
        inClassOrder.putAll(byClass);

        List<StandingsRowDto> ranked = new ArrayList<>();
        for (List<StandingsRowDto> classRows : inClassOrder.values()) {
            classRows.sort(Comparator.comparingInt(StandingsRowDto::totalPoints).reversed()
                    .thenComparing(ChampionshipStandingsQuery::countbackCompare)
                    .thenComparing(StandingsRowDto::displayName));
            StandingsRowDto previous = null;
            int position = 0;
            for (int i = 0; i < classRows.size(); i++) {
                StandingsRowDto row = classRows.get(i);
                if (previous == null || row.totalPoints() != previous.totalPoints()
                        || countbackCompare(row, previous) != 0) {
                    position = i + 1;
                }
                ranked.add(new StandingsRowDto(row.driverId(), row.displayName(), row.racingClassId(),
                        row.totalPoints(), position, row.rounds()));
                previous = row;
            }
        }
        return ranked;
    }

    /**
     * Countback between two drivers level on points: the one with more wins ahead, then more seconds, and so on
     * down the places. Only rounds that count are used (not dropped, not excluded, and raced), by the round's
     * position, so a dropped win does not help. Negative when {@code a} is ahead.
     */
    private static int countbackCompare(StandingsRowDto a, StandingsRowDto b) {
        Map<Integer, Long> countA = countedPositions(a);
        Map<Integer, Long> countB = countedPositions(b);
        int last = Stream.concat(countA.keySet().stream(), countB.keySet().stream())
                .mapToInt(Integer::intValue).max().orElse(0);
        for (int place = 1; place <= last; place++) {
            int byPlace = Long.compare(countB.getOrDefault(place, 0L), countA.getOrDefault(place, 0L));
            if (byPlace != 0) {
                return byPlace;
            }
        }
        return 0;
    }

    /** How many counted rounds the row has at each position. */
    private static Map<Integer, Long> countedPositions(StandingsRowDto row) {
        return row.rounds().stream()
                .filter(r -> !r.dropped() && !r.excluded() && r.position() > 0)
                .collect(Collectors.groupingBy(RoundResultDto::position, Collectors.counting()));
    }
}
