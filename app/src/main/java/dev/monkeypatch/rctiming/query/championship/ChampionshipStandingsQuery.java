package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
 * <p>A driver scores, per class, the points for their best position at each round. A driver on the grid at a
 * round who has no result in it has a DNS round worth nothing, which still counts towards Y. The TQ and A-final
 * winner bonuses are per driver, per class and per round: winning in two classes, or two rounds, earns each.
 * They come from every finished race, whatever the scoring source. A round a driver is excluded from earns them
 * nothing, bonuses included. The worst rounds are then dropped to the best X from Y, and each class is ranked.
 *
 * <p>Drivers are competitors (L5, #13): entries map to {@code entries.competitor_id}, so a driver with no login
 * still scores, and exclusions are keyed by competitor id. Reads only through the jOOQ DSL, with no repositories.
 */
@Service
@ReadTransaction
public class ChampionshipStandingsQuery {

    private final DSLContext dsl;
    private final ResultSnapshotJson snapshotJson;

    public ChampionshipStandingsQuery(DSLContext dsl, ResultSnapshotJson snapshotJson) {
        this.dsl = dsl;
        this.snapshotJson = snapshotJson;
    }

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

    // ── The championship's rules ───────────────────────────────────────────────────

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

    // ── The rounds and their results ───────────────────────────────────────────────

    /** An event linked to the championship. */
    private record ChampionshipRound(Long eventId, int roundNumber, String eventName) {}

    /** A finished race at a linked event, with its result rows best first. */
    private record FinishedRace(Long eventId, String roundType, String finalLetter, Long racingClassId,
                                boolean scores, List<ResultSnapshotDto.ResultRow> positions) {}

    /** The competitor and racing class of an entry on the grid of a finished race. */
    private record EntryRef(Long driverId, Long racingClassId) {}

    /**
     * One event's finished races, and the entries on their grids by entry id: those of the races that score, and
     * those of every finished race, which the bonuses are paid from.
     */
    private record EventResults(List<FinishedRace> races, Map<Long, EntryRef> scoringEntries,
                                Map<Long, EntryRef> allEntries, Map<Long, String> displayNames) {

        static final EventResults NONE = new EventResults(List.of(), Map.of(), Map.of(), Map.of());
    }

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
        dsl.select(RACES.ID, RACES.FINAL_LETTER, ROUNDS.TYPE, ROUNDS.EVENT_ID, EVENT_CLASSES.RACING_CLASS_ID,
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
                    FinishedRace race = new FinishedRace(r.get(ROUNDS.EVENT_ID), roundType, r.get(RACES.FINAL_LETTER),
                            r.get(EVENT_CLASSES.RACING_CLASS_ID), scores(scoringSource, roundType),
                            snapshotJson.positions(raceId, r.get(RESULT_SNAPSHOTS.POSITIONS_JSON)));
                    races.put(raceId, race);
                    results.computeIfAbsent(race.eventId(), k -> new EventResults(new ArrayList<>(),
                            new HashMap<>(), new HashMap<>(), new HashMap<>())).races().add(race);
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
                    if (race.scores()) {
                        event.scoringEntries().put(entryId, entry);
                    }
                    String displayName = r.get(COMPETITORS.DISPLAY_NAME);
                    if (entry.driverId() != null && displayName != null) {
                        event.displayNames().put(entry.driverId(), displayName);
                    }
                });
        return results;
    }

    /** Whether a race in a round of this type counts for points. */
    private static boolean scores(String scoringSource, String roundType) {
        if ("QUALIFYING".equals(scoringSource)) {
            return "QUALIFIER".equals(roundType);
        }
        if ("FINALS".equals(scoringSource)) {
            return "FINAL".equals(roundType);
        }
        return true; // BOTH
    }

    // ── Scoring each round ─────────────────────────────────────────────────────────

    /** A driver in one racing class: one row of the standings. */
    private record DriverClass(Long driverId, Long racingClassId) {}

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

    private static void score(ChampionshipRound round, EventResults results, Rules rules, Tally tally) {
        Long eventId = round.eventId();
        tally.displayNames.putAll(results.displayNames());

        // Each driver's best position in each racing class, over every race that scores
        Map<Long, Map<Long, Integer>> bestPositions = new HashMap<>();
        for (FinishedRace race : results.races()) {
            if (!race.scores()) {
                continue;
            }
            for (ResultSnapshotDto.ResultRow row : race.positions()) {
                EntryRef entry = results.scoringEntries().get(row.entryId());
                if (entry == null || entry.driverId() == null) {
                    continue;
                }
                bestPositions.computeIfAbsent(race.racingClassId(), k -> new HashMap<>())
                        .merge(entry.driverId(), row.position(), Math::min);
            }
        }

        bonuses(eventId, results, rules, tally);

        bestPositions.forEach((racingClassId, positions) -> positions.forEach((driverId, position) -> {
            boolean excluded = rules.excluded(driverId, eventId);
            tally.add(new DriverClass(driverId, racingClassId), new RoundResultDto(round.roundNumber(), eventId,
                    round.eventName(), excluded ? 0 : position, excluded ? 0 : rules.points(position), excluded,
                    false));
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

    /** Who topped a qualifier (TQ) or won the A final at the round, from every finished race. */
    private static void bonuses(Long eventId, EventResults results, Rules rules, Tally tally) {
        for (FinishedRace race : results.races()) {
            boolean qualifier = "QUALIFIER".equals(race.roundType());
            boolean aFinal = "FINAL".equals(race.roundType()) && "A".equals(race.finalLetter());
            for (ResultSnapshotDto.ResultRow row : race.positions()) {
                EntryRef entry = results.allEntries().get(row.entryId());
                if (entry == null || entry.driverId() == null || row.position() != 1
                        || rules.excluded(entry.driverId(), eventId)) {
                    continue;
                }
                ClassRound won = new ClassRound(new DriverClass(entry.driverId(), race.racingClassId()), eventId);
                if (qualifier) {
                    tally.tqWins.add(won);
                }
                if (aFinal) {
                    tally.aFinalWins.add(won);
                }
            }
        }
    }

    // ── Drops and ranking ──────────────────────────────────────────────────────────

    private static List<StandingsRowDto> rows(Tally tally, Rules rules) {
        List<StandingsRowDto> rows = new ArrayList<>();
        tally.rounds.forEach((driverClass, rounds) -> {
            List<RoundResultDto> marked = dropWorst(rounds, rules.bestXFromY(driverClass.racingClassId()));
            int totalPoints = marked.stream()
                    .filter(r -> !r.dropped() && !r.excluded())
                    .mapToInt(RoundResultDto::points)
                    .sum();
            totalPoints += rules.tqBonus() * roundsWon(tally.tqWins, driverClass);
            totalPoints += rules.aFinalBonus() * roundsWon(tally.aFinalWins, driverClass);
            rows.add(new StandingsRowDto(driverClass.driverId(),
                    tally.displayNames.getOrDefault(driverClass.driverId(), ""),
                    driverClass.racingClassId(), totalPoints, marked));
        });
        return rows;
    }

    /**
     * The rounds in round order, the worst Y minus X marked dropped once the driver has Y rounds. Worst means
     * fewest points, then the lower position, so a finish worth nothing is dropped before a DNS.
     */
    private static List<RoundResultDto> dropWorst(List<RoundResultDto> rounds, BestXFromY rule) {
        int count = rounds.size();
        int x = rule.x() != null ? rule.x() : count;
        int y = rule.y() != null ? rule.y() : count;
        int dropFrom = y > x && count >= y ? Math.max(0, count - (y - x)) : count;

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
     * Each class by points, then display name. Classes come out in racing class order (a class with no racing
     * class last): the order rows are first seen in follows hash-map order, which changes with the ids.
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
                    .thenComparing(StandingsRowDto::displayName));
            ranked.addAll(classRows);
        }
        return ranked;
    }
}
