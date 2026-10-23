package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.championship.ChampionshipStandingsQuery;
import dev.monkeypatch.rctiming.service.QualifyingStandingsService;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.monkeypatch.rctiming.jooq.generated.tables.ClubProfiles.CLUB_PROFILES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.EventClasses.EVENT_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.RacingClasses.RACING_CLASSES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Races.RACES;
import static dev.monkeypatch.rctiming.jooq.generated.tables.ResultSnapshots.RESULT_SNAPSHOTS;
import static dev.monkeypatch.rctiming.jooq.generated.tables.Rounds.ROUNDS;

/**
 * Reads a finished race's stored result. The positions are the corrected ones, as
 * {@code ResultSnapshotService} last wrote them: the timed result plus every penalty and lap adjustment given
 * since.
 */
@Component
@ReadTransaction
public class ResultSnapshotQuery {

    private final DSLContext dsl;
    private final ResultSnapshotJson snapshotJson;
    private final QualifyingStandingsService qualifyingStandings;
    private final ChampionshipStandingsQuery championshipStandings;

    public ResultSnapshotQuery(DSLContext dsl, ResultSnapshotJson snapshotJson,
                               QualifyingStandingsService qualifyingStandings,
                               ChampionshipStandingsQuery championshipStandings) {
        this.dsl = dsl;
        this.snapshotJson = snapshotJson;
        this.qualifyingStandings = qualifyingStandings;
        this.championshipStandings = championshipStandings;
    }

    /**
     * The stored result of a race with its label, lap-by-lap position history and the club's name and logo for
     * printing. Times in the rows are milliseconds; {@code finishedAt} is the snapshot's own finish time. The rows of
     * a qualifying heat of a low-points class carry the points each driver scored in that heat's round, and the rows
     * of a final carry what the driver's place in the event's finals is worth in its championship.
     *
     * @throws EntityNotFoundException if the race has no stored result (not finished yet, or no such race)
     */
    public ResultSnapshotDto load(long raceId) {
        return load(raceId, new ResultPointsCache());
    }

    /**
     * As {@link #load(long)}, reusing {@code cache} (filled as it goes) so a caller reading many races of one event
     * scores each class and each event once.
     *
     * @throws EntityNotFoundException if the race has no stored result
     */
    public ResultSnapshotDto load(long raceId, ResultPointsCache cache) {
        var row = dsl
                .select(
                        RESULT_SNAPSHOTS.RACE_ID,
                        RESULT_SNAPSHOTS.FINISHED_AT,
                        RESULT_SNAPSHOTS.POSITIONS_JSON,
                        RESULT_SNAPSHOTS.LAP_HISTORY_JSON,
                        RACES.ROUND_ID,
                        RACES.EVENT_CLASS_ID,
                        ROUNDS.EVENT_ID,
                        ROUNDS.ROUND_NUMBER,
                        ROUNDS.TYPE,
                        RACES.HEAT_NUMBER,
                        RACES.FINAL_LETTER,
                        RACING_CLASSES.NAME.as("className"))
                .from(RESULT_SNAPSHOTS)
                .join(RACES).on(RACES.ID.eq(RESULT_SNAPSHOTS.RACE_ID))
                .join(ROUNDS).on(ROUNDS.ID.eq(RACES.ROUND_ID))
                .join(EVENT_CLASSES).on(EVENT_CLASSES.ID.eq(RACES.EVENT_CLASS_ID))
                .join(RACING_CLASSES).on(RACING_CLASSES.ID.eq(EVENT_CLASSES.RACING_CLASS_ID))
                .where(RESULT_SNAPSHOTS.RACE_ID.eq(raceId))
                .fetchOne();

        if (row == null) {
            throw new EntityNotFoundException("No result snapshot for race " + raceId);
        }

        String roundType = row.get(ROUNDS.TYPE);
        int roundNumber = row.get(ROUNDS.ROUND_NUMBER);
        String className = row.get(RACING_CLASSES.NAME.as("className"), String.class);
        int heatNumber = row.get(RACES.HEAT_NUMBER);
        String finalLetter = row.get(RACES.FINAL_LETTER);
        String raceLabel = RaceLabel.of(roundType, roundNumber, className, heatNumber, finalLetter);

        Instant finishedAt = row.get(RESULT_SNAPSHOTS.FINISHED_AT);

        List<ResultSnapshotDto.ResultRow> positions =
                snapshotJson.positions(raceId, row.get(RESULT_SNAPSHOTS.POSITIONS_JSON));
        if (RoundType.QUALIFIER.name().equals(roundType)) {
            positions = withRoundPoints(positions, row.get(RACES.EVENT_CLASS_ID), row.get(RACES.ROUND_ID), cache);
        } else if (RoundType.FINAL.name().equals(roundType)) {
            positions = withChampionshipPoints(positions, row.get(ROUNDS.EVENT_ID), raceId, cache);
        }
        List<ResultSnapshotDto.PositionAtLap> lapHistory =
                snapshotJson.lapHistory(raceId, row.get(RESULT_SNAPSHOTS.LAP_HISTORY_JSON));
        ResultSnapshotDto.ClubBrandingDto branding = fetchClubBranding();

        return new ResultSnapshotDto(raceId, raceLabel, finishedAt, positions, lapHistory, branding);
    }

    /**
     * The rows with the points each driver scored in the heat's round, when the class ranks qualifying round by
     * round and the round is fully finished; otherwise the rows as they are.
     */
    private List<ResultSnapshotDto.ResultRow> withRoundPoints(List<ResultSnapshotDto.ResultRow> rows,
                                                              long eventClassId, long roundId,
                                                              ResultPointsCache cache) {
        Map<Long, Integer> points = cache.roundPoints
                .computeIfAbsent(eventClassId, qualifyingStandings::roundPointsFor).get(roundId);
        if (points == null) {
            return rows;
        }
        return rows.stream().map(r -> r.withPoints(points.get(r.entryId()))).toList();
    }

    /**
     * The rows of a final with the championship points each finalist's place in the event's finals earns, when the
     * event belongs to a championship that scores the final's class and the final is placed; otherwise the rows as
     * they are.
     */
    private List<ResultSnapshotDto.ResultRow> withChampionshipPoints(List<ResultSnapshotDto.ResultRow> rows,
                                                                     long eventId, long raceId,
                                                                     ResultPointsCache cache) {
        Map<Long, Integer> points = cache.finalPoints
                .computeIfAbsent(eventId, championshipStandings::finalPointsFor).get(raceId);
        if (points == null) {
            return rows;
        }
        return rows.stream().map(r -> r.withChampionshipPoints(points.get(r.entryId()))).toList();
    }

    private ResultSnapshotDto.ClubBrandingDto fetchClubBranding() {
        var club = dsl.select(CLUB_PROFILES.NAME, CLUB_PROFILES.LOGO_URL)
                .from(CLUB_PROFILES)
                .limit(1)
                .fetchOne();
        if (club == null) return null;
        String logoUrl = club.get(CLUB_PROFILES.LOGO_URL);
        if (logoUrl == null || logoUrl.isBlank()) {
            return new ResultSnapshotDto.ClubBrandingDto(club.get(CLUB_PROFILES.NAME), null);
        }
        return new ResultSnapshotDto.ClubBrandingDto(club.get(CLUB_PROFILES.NAME), logoUrl);
    }
}
