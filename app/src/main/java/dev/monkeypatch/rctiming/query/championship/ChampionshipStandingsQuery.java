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
import java.util.Objects;
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
 * CHAMP-01, CHAMP-02, CHAMP-04, CHAMP-07, CHAMP-08, CHAMP-09 read side.
 *
 * Implements full championship standings computation: best-X-from-Y drop logic,
 * TQ bonus, A-final winner bonus, DNS semantics. The two bonuses are per driver, per class and per
 * round (an event linked to the championship): winning in two classes, or two rounds, earns each.
 * A round a driver is excluded from earns no bonus either.
 * Reads only through the jOOQ DSL, with no repositories.
 *
 * Drivers are competitors (L5, #13): entries map to {@code entries.competitor_id}, so a driver
 * with no login still scores, and exclusions are keyed by competitor id.
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
        if (!dsl.fetchExists(CHAMPIONSHIPS, CHAMPIONSHIPS.ID.eq(championshipId))) {
            throw new EntityNotFoundException("Championship not found: " + championshipId);
        }

        var champRecord = dsl
                .select(CHAMPIONSHIPS.NAME, CHAMPIONSHIPS.BEST_X_FROM_Y_X, CHAMPIONSHIPS.BEST_X_FROM_Y_Y,
                        CHAMPIONSHIPS.SCORING_SOURCE, CHAMPIONSHIPS.TQ_BONUS_POINTS,
                        CHAMPIONSHIPS.AFINAL_WINNER_BONUS_POINTS)
                .from(CHAMPIONSHIPS)
                .where(CHAMPIONSHIPS.ID.eq(championshipId))
                .fetchOne();

        if (champRecord == null) {
            throw new EntityNotFoundException("Championship not found: " + championshipId);
        }

        Integer champBestX = champRecord.get(CHAMPIONSHIPS.BEST_X_FROM_Y_X);
        Integer champBestY = champRecord.get(CHAMPIONSHIPS.BEST_X_FROM_Y_Y);
        String scoringSource = champRecord.get(CHAMPIONSHIPS.SCORING_SOURCE);
        int tqBonus = champRecord.get(CHAMPIONSHIPS.TQ_BONUS_POINTS);
        int afinalBonus = champRecord.get(CHAMPIONSHIPS.AFINAL_WINNER_BONUS_POINTS);

        // Load per-class best-X-from-Y overrides
        // Map: racingClassId -> [bestX, bestY] (null = use championship default)
        var classRows = dsl
                .select(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID,
                        CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_X,
                        CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_Y)
                .from(CHAMPIONSHIP_CLASSES)
                .where(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId))
                .fetch();

        Map<Long, Integer[]> classOverrides = new HashMap<>();
        for (var row : classRows) {
            Long rcId = row.get(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID);
            classOverrides.put(rcId, new Integer[]{
                    row.get(CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_X),
                    row.get(CHAMPIONSHIP_CLASSES.BEST_X_FROM_Y_Y)
            });
        }

        // Load ordered event links
        var eventLinks = dsl
                .select(CHAMPIONSHIP_EVENT_LINKS.ID,
                        CHAMPIONSHIP_EVENT_LINKS.EVENT_ID,
                        CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER,
                        EVENTS.NAME)
                .from(CHAMPIONSHIP_EVENT_LINKS)
                .join(EVENTS).on(EVENTS.ID.eq(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID))
                .where(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.eq(championshipId))
                .orderBy(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER.asc())
                .fetch();

        // Load points scale
        Map<Integer, Integer> pointsScale = new HashMap<>();
        dsl.select(CHAMPIONSHIP_POINTS_SCALE.POSITION, CHAMPIONSHIP_POINTS_SCALE.POINTS)
                .from(CHAMPIONSHIP_POINTS_SCALE)
                .where(CHAMPIONSHIP_POINTS_SCALE.CHAMPIONSHIP_ID.eq(championshipId))
                .forEach(r -> pointsScale.put(r.get(CHAMPIONSHIP_POINTS_SCALE.POSITION),
                        r.get(CHAMPIONSHIP_POINTS_SCALE.POINTS)));

        // Load exclusions
        Set<String> exclusionKeys = new HashSet<>();
        dsl.select(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID, CHAMPIONSHIP_EXCLUSIONS.EVENT_ID)
                .from(CHAMPIONSHIP_EXCLUSIONS)
                .where(CHAMPIONSHIP_EXCLUSIONS.CHAMPIONSHIP_ID.eq(championshipId))
                .forEach(r -> exclusionKeys.add(
                        r.get(CHAMPIONSHIP_EXCLUSIONS.DRIVER_ID) + ":" + r.get(CHAMPIONSHIP_EXCLUSIONS.EVENT_ID)));

        // driverKey = "driverId:racingClassId"
        // Store: Map<driverKey, List<RoundResultDto>> for building standings
        Map<String, List<RoundResultDto>> driverRounds = new LinkedHashMap<>();
        // For StandingsRowDto metadata
        Map<Long, String> driverDisplayName = new HashMap<>();
        // Map driverKey to racingClassId
        Map<String, Long> driverKeyToClassId = new HashMap<>();
        // Map driverKey to driverId
        Map<String, Long> driverKeyToDriverId = new HashMap<>();

        // TQ and A-final bonus tracking (CHAMP-07, CHAMP-08): one bonus per driver, per class, per round.
        // A driver who tops qualifying in two classes, or in two rounds, is paid for each.
        Set<ClassRound> tqBonuses = new HashSet<>();      // position 1 in a QUALIFIER race
        Set<ClassRound> afinalBonuses = new HashSet<>();  // position 1 in an A final

        // Per event link — find finished races and build RoundResultDtos
        for (var link : eventLinks) {
            Long eventId = link.get(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID);
            int roundNumber = link.get(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER);
            String eventName = link.get(EVENTS.NAME);

            // Build round type filter based on scoringSource
            var racesQuery = dsl
                    .select(RACES.ID, RACES.FINAL_LETTER, ROUNDS.TYPE, ROUNDS.ROUND_NUMBER,
                            EVENT_CLASSES.RACING_CLASS_ID,
                            RESULT_SNAPSHOTS.POSITIONS_JSON)
                    .from(RACES)
                    .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                    .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                    .leftJoin(RESULT_SNAPSHOTS).on(RESULT_SNAPSHOTS.RACE_ID.eq(RACES.ID))
                    .where(ROUNDS.EVENT_ID.eq(eventId))
                    .and(RACES.STATUS.eq("FINISHED"));

            if ("QUALIFYING".equals(scoringSource)) {
                racesQuery = racesQuery.and(ROUNDS.TYPE.eq("QUALIFIER"));
            } else if ("FINALS".equals(scoringSource)) {
                racesQuery = racesQuery.and(ROUNDS.TYPE.eq("FINAL"));
            }
            // BOTH = no additional filter on type

            var finishedRaces = racesQuery.fetch();

            // Collect raceIds for DNS detection
            List<Long> finishedRaceIds = finishedRaces.map(r -> r.get(RACES.ID));

            // Per-driver best position at this event, per racing class
            // Map: racingClassId -> Map<driverId, Integer bestPosition>
            Map<Long, Map<Long, Integer>> classBestPosition = new HashMap<>();
            // Per-race: track who scored what for TQ/A-final bonus
            // positionsJson entryId → driverId (competitor) mapping needed

            // For all race_entries for these races, collect each entered driver's competitor id
            Set<Long> enteredEntryIds = new HashSet<>();
            // Map entryId -> competitor id and entryId -> racingClassId (via entry -> event_class)
            Map<Long, Long> entryIdToDriverId = new HashMap<>();
            Map<Long, Long> entryIdToRacingClassId = new HashMap<>();

            if (!finishedRaceIds.isEmpty()) {
                var raceEntryRows = dsl
                        .select(RACE_ENTRIES.ENTRY_ID, ENTRIES.COMPETITOR_ID, ENTRIES.EVENT_CLASS_ID)
                        .from(RACE_ENTRIES)
                        .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                        .where(RACE_ENTRIES.RACE_ID.in(finishedRaceIds))
                        .fetch();

                for (var re : raceEntryRows) {
                    Long entryId = re.get(RACE_ENTRIES.ENTRY_ID);
                    Long driverId = re.get(ENTRIES.COMPETITOR_ID);
                    Long ecId = re.get(ENTRIES.EVENT_CLASS_ID);
                    enteredEntryIds.add(entryId);
                    entryIdToDriverId.put(entryId, driverId);
                    entryIdToRacingClassId.put(entryId, ecId);
                }

                // Resolve event_class_id -> racing_class_id
                Map<Long, Long> ecToRacingClass = new HashMap<>();
                Set<Long> ecIds = new HashSet<>(entryIdToRacingClassId.values());
                if (!ecIds.isEmpty()) {
                    dsl.select(EVENT_CLASSES.ID, EVENT_CLASSES.RACING_CLASS_ID)
                            .from(EVENT_CLASSES)
                            .where(EVENT_CLASSES.ID.in(ecIds))
                            .forEach(r -> ecToRacingClass.put(r.get(EVENT_CLASSES.ID),
                                    r.get(EVENT_CLASSES.RACING_CLASS_ID)));
                }
                // Update entryIdToRacingClassId to actual racingClassId
                entryIdToRacingClassId.replaceAll((eid, ecId) ->
                        ecToRacingClass.getOrDefault(ecId, ecId));
            }

            // Load competitor names for all entered drivers
            Set<Long> allDriverIds = new HashSet<>(entryIdToDriverId.values());
            allDriverIds.remove(null);
            if (!allDriverIds.isEmpty()) {
                dsl.select(COMPETITORS.ID, COMPETITORS.DISPLAY_NAME)
                        .from(COMPETITORS)
                        .where(COMPETITORS.ID.in(allDriverIds))
                        .forEach(r -> driverDisplayName.put(r.get(COMPETITORS.ID), r.get(COMPETITORS.DISPLAY_NAME)));
            }

            // Deserialize positions_json and collect per-driver best position per class
            for (var race : finishedRaces) {
                var raceEventClassId = race.get(EVENT_CLASSES.RACING_CLASS_ID);
                var positions = snapshotJson.positions(race.get(RACES.ID), race.get(RESULT_SNAPSHOTS.POSITIONS_JSON));
                for (ResultSnapshotDto.ResultRow row : positions) {
                    Long driverId = entryIdToDriverId.get(row.entryId());
                    if (driverId == null) continue;

                    // Track best position per driver per racing class at this event
                    classBestPosition
                            .computeIfAbsent(raceEventClassId, k -> new HashMap<>())
                            .merge(driverId, row.position(), Math::min);
                }
            }

            // Bonus tracking uses ALL finished races at this event regardless of scoringSource.
            // When scoringSource=FINALS, finishedRaces contains no QUALIFIERs, so TQ bonus was never
            // awarded. Separate query ensures bonuses are always evaluated from the correct race type.
            var bonusRaces = dsl
                    .select(RACES.ID, RACES.FINAL_LETTER, ROUNDS.TYPE, EVENT_CLASSES.RACING_CLASS_ID,
                            RESULT_SNAPSHOTS.POSITIONS_JSON)
                    .from(RACES)
                    .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                    .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                    .leftJoin(RESULT_SNAPSHOTS).on(RESULT_SNAPSHOTS.RACE_ID.eq(RACES.ID))
                    .where(ROUNDS.EVENT_ID.eq(eventId))
                    .and(RACES.STATUS.eq("FINISHED"))
                    .fetch();

            List<Long> bonusRaceIds = bonusRaces.map(r -> r.get(RACES.ID));
            Map<Long, Long> bonusEntryToDriver = new HashMap<>();
            if (!bonusRaceIds.isEmpty()) {
                dsl.select(RACE_ENTRIES.ENTRY_ID, ENTRIES.COMPETITOR_ID)
                        .from(RACE_ENTRIES)
                        .join(ENTRIES).on(ENTRIES.ID.eq(RACE_ENTRIES.ENTRY_ID))
                        .where(RACE_ENTRIES.RACE_ID.in(bonusRaceIds))
                        .forEach(r -> bonusEntryToDriver.put(r.get(RACE_ENTRIES.ENTRY_ID),
                                r.get(ENTRIES.COMPETITOR_ID)));
            }

            for (var race : bonusRaces) {
                String roundType = race.get(ROUNDS.TYPE);
                String finalLetter = race.get(RACES.FINAL_LETTER);
                Long bonusClassId = race.get(EVENT_CLASSES.RACING_CLASS_ID);
                var positions = snapshotJson.positions(race.get(RACES.ID), race.get(RESULT_SNAPSHOTS.POSITIONS_JSON));
                for (ResultSnapshotDto.ResultRow row : positions) {
                    Long driverId = bonusEntryToDriver.get(row.entryId());
                    if (driverId == null) continue;
                    // An excluded driver earns nothing from that round, bonuses included (CHAMP-09)
                    if (exclusionKeys.contains(driverId + ":" + eventId)) continue;
                    ClassRound won = new ClassRound(driverId, bonusClassId, eventId);
                    if ("QUALIFIER".equals(roundType) && row.position() == 1) {
                        tqBonuses.add(won);
                    }
                    if ("FINAL".equals(roundType) && "A".equals(finalLetter) && row.position() == 1) {
                        afinalBonuses.add(won);
                    }
                }
            }

            // Build RoundResultDto for each driver that appeared in positions OR race_entries
            // Collect all (driverId, racingClassId) pairs seen at this event
            // From positions (via classBestPosition)
            for (var classEntry : classBestPosition.entrySet()) {
                Long rcId = classEntry.getKey();
                for (var driverEntry : classEntry.getValue().entrySet()) {
                    Long driverId = driverEntry.getKey();
                    int position = driverEntry.getValue();
                    String exKey = driverId + ":" + eventId;
                    boolean excluded = exclusionKeys.contains(exKey);
                    int points = excluded ? 0 : pointsScale.getOrDefault(position, 0);

                    String driverKey = driverId + ":" + rcId;
                    driverKeyToClassId.put(driverKey, rcId);
                    driverKeyToDriverId.put(driverKey, driverId);
                    driverRounds.computeIfAbsent(driverKey, k -> new ArrayList<>())
                            .add(new RoundResultDto(roundNumber, eventId, eventName,
                                    excluded ? 0 : position, points, excluded, false));
                }
            }

            // DNS drivers: in race_entries but not in positions at this event for their class
            // Deduplicate by (driverId, rcId, eventId) — a driver with multiple heats can
            // appear in entryIdToDriverId multiple times, inflating their DNS round count.
            Set<String> dnsEmitted = new HashSet<>();
            for (var entryIdEntry : entryIdToDriverId.entrySet()) {
                Long entryId = entryIdEntry.getKey();
                Long driverId = entryIdEntry.getValue();
                Long rcId = entryIdToRacingClassId.get(entryId);
                if (driverId == null || rcId == null) continue;

                Long finalRcId = rcId;
                boolean appearedInPositions = classBestPosition
                        .getOrDefault(finalRcId, Map.of())
                        .containsKey(driverId);

                if (!appearedInPositions) {
                    // A DNS is a round toward Y, scoring zero (CHAMP-02)
                    String dnsKey = driverId + ":" + rcId + ":" + eventId;
                    if (dnsEmitted.add(dnsKey)) {
                        String driverKey = driverId + ":" + rcId;
                        driverKeyToClassId.put(driverKey, rcId);
                        driverKeyToDriverId.put(driverKey, driverId);
                        boolean excluded = exclusionKeys.contains(driverId + ":" + eventId);
                        driverRounds.computeIfAbsent(driverKey, k -> new ArrayList<>())
                                .add(new RoundResultDto(roundNumber, eventId, eventName,
                                        0, 0, excluded, false));
                    }
                }
            }
        }

        // Apply best-X-from-Y drop logic per driver-class group
        // Collect all distinct (driverId, racingClassId) combinations and build standings
        Map<Long, List<StandingsRowDto>> byClass = new LinkedHashMap<>();

        for (var driverKey : driverRounds.keySet()) {
            Long driverId = driverKeyToDriverId.get(driverKey);
            Long rcId = driverKeyToClassId.get(driverKey);
            List<RoundResultDto> rounds = new ArrayList<>(driverRounds.get(driverKey));

            // Determine effective X and Y
            Integer[] classOvr = classOverrides.get(rcId);
            Integer effectiveX = classOvr != null && classOvr[0] != null ? classOvr[0] : champBestX;
            Integer effectiveY = classOvr != null && classOvr[1] != null ? classOvr[1] : champBestY;
            int totalRounds = rounds.size();
            int useX = effectiveX != null ? effectiveX : totalRounds;
            int useY = effectiveY != null ? effectiveY : totalRounds;

            // Sort by points DESC to identify worst rounds to drop
            List<RoundResultDto> sorted = new ArrayList<>(rounds);
            sorted.sort(Comparator.comparingInt(RoundResultDto::points).reversed()
                    .thenComparingInt(RoundResultDto::position)); // worst position (highest) dropped first on tie

            // Mark bottom (Y - X) rounds as dropped, but only if we have >= Y rounds
            Set<Integer> droppedIndices = new HashSet<>();
            if (useY > useX && totalRounds >= useY) {
                int dropCount = useY - useX;
                // The last dropCount entries in sorted (lowest points) get dropped
                for (int i = sorted.size() - dropCount; i < sorted.size(); i++) {
                    if (i >= 0) droppedIndices.add(i);
                }
            }

            // Rebuild rounds with dropped flags set; we need to map back to original order
            // Create a list with dropped flags based on sorted index identity
            // Use object identity to match sorted back to original
            List<RoundResultDto> withDropped = new ArrayList<>();
            for (int si = 0; si < sorted.size(); si++) {
                RoundResultDto original = sorted.get(si);
                boolean drop = droppedIndices.contains(si);
                withDropped.add(new RoundResultDto(
                        original.roundNumber(), original.eventId(), original.eventName(),
                        original.position(), original.points(), original.excluded(), drop));
            }

            // Restore chronological order (sort by roundNumber)
            withDropped.sort(Comparator.comparingInt(RoundResultDto::roundNumber));

            // Total points = sum of non-dropped, non-excluded rounds
            int totalPoints = withDropped.stream()
                    .filter(r -> !r.dropped() && !r.excluded())
                    .mapToInt(RoundResultDto::points)
                    .sum();

            // Apply TQ and A-final bonuses: one for each round this driver won in this class
            totalPoints += tqBonus * roundsWon(tqBonuses, driverId, rcId);
            totalPoints += afinalBonus * roundsWon(afinalBonuses, driverId, rcId);

            String displayName = driverDisplayName.getOrDefault(driverId, "");

            StandingsRowDto row = new StandingsRowDto(driverId, displayName,
                    rcId, totalPoints, withDropped);

            byClass.computeIfAbsent(rcId, k -> new ArrayList<>()).add(row);
        }

        // Sort each class by totalPoints DESC, display name as tiebreak. Classes come out in
        // racing class order: the order the rows were first seen in follows hash-map iteration, which
        // changes with the ids and made the output order depend on how many classes existed before.
        List<StandingsRowDto> result = new ArrayList<>();
        // (nullsLast: a class can have no racing class assigned)
        Map<Long, List<StandingsRowDto>> inClassOrder = new TreeMap<>(Comparator.nullsLast(Comparator.<Long>naturalOrder()));
        inClassOrder.putAll(byClass);
        for (var classRows2 : inClassOrder.values()) {
            classRows2.sort(Comparator.comparingInt(StandingsRowDto::totalPoints).reversed()
                    .thenComparing(StandingsRowDto::displayName));
            result.addAll(classRows2);
        }

        return result;
    }

    /** A driver winning something in one class at one event (a round of the championship). */
    private record ClassRound(Long driverId, Long racingClassId, Long eventId) {}

    /** How many rounds the driver won in the class: each is paid its bonus once. */
    private static int roundsWon(Set<ClassRound> wins, Long driverId, Long racingClassId) {
        return (int) wins.stream()
                .filter(w -> w.driverId().equals(driverId) && Objects.equals(w.racingClassId(), racingClassId))
                .count();
    }
}
