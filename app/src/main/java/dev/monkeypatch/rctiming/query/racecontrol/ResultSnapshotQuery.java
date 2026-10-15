package dev.monkeypatch.rctiming.query.racecontrol;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.race.RaceLabel;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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

    public ResultSnapshotQuery(DSLContext dsl, ResultSnapshotJson snapshotJson) {
        this.dsl = dsl;
        this.snapshotJson = snapshotJson;
    }

    public ResultSnapshotDto load(long raceId) {
        var row = dsl
                .select(
                        RESULT_SNAPSHOTS.RACE_ID,
                        RESULT_SNAPSHOTS.FINISHED_AT,
                        RESULT_SNAPSHOTS.POSITIONS_JSON,
                        RESULT_SNAPSHOTS.LAP_HISTORY_JSON,
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
        List<ResultSnapshotDto.PositionAtLap> lapHistory =
                snapshotJson.lapHistory(raceId, row.get(RESULT_SNAPSHOTS.LAP_HISTORY_JSON));
        ResultSnapshotDto.ClubBrandingDto branding = fetchClubBranding();

        return new ResultSnapshotDto(raceId, raceLabel, finishedAt, positions, lapHistory, branding);
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
