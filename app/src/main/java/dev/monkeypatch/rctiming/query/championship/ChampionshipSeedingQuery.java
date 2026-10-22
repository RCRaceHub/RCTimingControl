package dev.monkeypatch.rctiming.query.championship;

import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipClasses.CHAMPIONSHIP_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ChampionshipEventLinks.CHAMPIONSHIP_EVENT_LINKS;

/**
 * Where each driver stands in the championship an event belongs to, for seeding the first qualifying round: the
 * best drivers are spread across the heats instead of being drawn in entry order. Each class is seeded from its own
 * table, so a 2WD class uses the 2WD standings and a 4WD class the 4WD standings.
 *
 * <p>The championship is the one the event is linked to and that scores the class (one with no classes added scores
 * every class). If there are several, the one where this event is the later round wins, then the lower id. The
 * first round of a championship has no standings, and a driver with no points yet, such as one new to the
 * championship, counts as having none and is seeded below those who have. The standings are the stored results of
 * the earlier rounds, read in the caller's transaction ({@code RoundGeneratorService}'s, on the write connection).
 */
@Service
@ReadTransaction
public class ChampionshipSeedingQuery {

    private final DSLContext dsl;
    private final ChampionshipStandingsQuery standingsQuery;

    public ChampionshipSeedingQuery(DSLContext dsl, ChampionshipStandingsQuery standingsQuery) {
        this.dsl = dsl;
        this.standingsQuery = standingsQuery;
    }

    /**
     * Each scoring driver's place in the class's championship table, by competitor id, 1 for the leader. Drivers with
     * no points are left out, as is everyone when the event is in no championship that scores the class or is its
     * first round. Never throws for a missing championship.
     *
     * @param eventId       the event whose first qualifying round is being seeded
     * @param racingClassId the racing class of the event class being seeded, or null for none
     * @return competitor id to position (equal points share one); empty when there is nothing to seed from
     */
    public Map<Long, Integer> positionsFor(Long eventId, Long racingClassId) {
        if (racingClassId == null) {
            return Map.of();
        }
        Long championshipId = championshipFor(eventId, racingClassId);
        if (championshipId == null) {
            return Map.of();
        }
        Map<Long, Integer> positions = new HashMap<>();
        for (StandingsRowDto row : standingsQuery.computeStandings(championshipId)) {
            if (racingClassId.equals(row.racingClassId()) && row.totalPoints() > 0 && row.driverId() != null) {
                positions.putIfAbsent(row.driverId(), row.position());
            }
        }
        return positions;
    }

    /** The championship to seed from, or null: none scores the class here, or this is its first round. */
    private Long championshipFor(Long eventId, Long racingClassId) {
        Long best = null;
        int bestRound = 1; // a first round has no earlier results, so only a later round qualifies
        for (var link : dsl.select(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID, CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER)
                .from(CHAMPIONSHIP_EVENT_LINKS)
                .where(CHAMPIONSHIP_EVENT_LINKS.EVENT_ID.eq(eventId))
                .orderBy(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID.asc())
                .fetch()) {
            Long championshipId = link.get(CHAMPIONSHIP_EVENT_LINKS.CHAMPIONSHIP_ID);
            int round = link.get(CHAMPIONSHIP_EVENT_LINKS.ROUND_NUMBER);
            if (round > bestRound && scores(championshipId, racingClassId)) {
                best = championshipId;
                bestRound = round;
            }
        }
        return best;
    }

    /** Whether the championship scores the class: it is listed, or the championship lists no classes at all. */
    private boolean scores(Long championshipId, Long racingClassId) {
        List<Long> classes = dsl.select(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID)
                .from(CHAMPIONSHIP_CLASSES)
                .where(CHAMPIONSHIP_CLASSES.CHAMPIONSHIP_ID.eq(championshipId))
                .fetch(CHAMPIONSHIP_CLASSES.RACING_CLASS_ID);
        Set<Long> listed = classes.stream().collect(Collectors.toSet());
        return listed.isEmpty() || listed.contains(racingClassId);
    }
}
