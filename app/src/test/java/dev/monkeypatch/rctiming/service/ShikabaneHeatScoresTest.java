package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceElimination;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RacePointPenalty;
import dev.monkeypatch.rctiming.domain.race.RacePointPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A car's penalty points in one finished Shikabane heat. */
class ShikabaneHeatScoresTest {

    private static final long HEAT = 4L;
    private static final ShikabaneConfig CONFIG = new ShikabaneConfig(5, StartType.GRID, 3, 3, 1);

    private final RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
    private final ResultSnapshotRepository snapshots = mock(ResultSnapshotRepository.class);
    private final ResultSnapshotJson json = mock(ResultSnapshotJson.class);
    private final RaceEliminationRepository eliminations = mock(RaceEliminationRepository.class);
    private final RacePointPenaltyRepository penalties = mock(RacePointPenaltyRepository.class);
    private ShikabaneHeatScores scores;
    private Race heat;

    @BeforeEach
    void setUp() {
        scores = new ShikabaneHeatScores(raceEntries, snapshots, json, eliminations, penalties);
        heat = new Race();
        heat.setId(HEAT);
        when(raceEntries.findByRaceIdOrderByGridPosition(HEAT)).thenReturn(List.of(grid(1), grid(2), grid(3), grid(4)));
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setPositionsJson("[]");
        when(snapshots.findByRaceId(HEAT)).thenReturn(Optional.of(snapshot));
        // 1 won, 2 second, 3 third; 4 never crossed the line and has no row
        when(json.positions(HEAT, "[]")).thenReturn(List.of(row(1, 1, 10), row(2, 2, 9), row(3, 3, 4)));
    }

    @Test
    void aCarsPointsAreItsPlaceAndACarWithNoRowIsPlacedLast() {
        assertThat(pointsByEntry()).containsEntry(1L, 1).containsEntry(2L, 2).containsEntry(3L, 3)
                .containsEntry(4L, 4);
    }

    @Test
    void aRevivalTakesTheBonusOffAndAFalseStartAddsItsPoints() {
        RaceElimination revived = new RaceElimination();
        revived.setEntryId(3L);
        revived.setRevivedAt(Instant.now());
        when(eliminations.findByRaceId(HEAT)).thenReturn(List.of(revived));
        RacePointPenalty falseStart = new RacePointPenalty();
        falseStart.setEntryId(1L);
        falseStart.setPoints(3);
        when(penalties.findByRaceId(HEAT)).thenReturn(List.of(falseStart));

        assertThat(pointsByEntry()).containsEntry(1L, 4).containsEntry(3L, 2);
    }

    @Test
    void anAbandonedHeatPlacesEveryoneLast() {
        heat.setAbandonedAt(Instant.now());

        assertThat(pointsByEntry().values()).containsOnly(1);
    }

    private Map<Long, Integer> pointsByEntry() {
        return scores.scores(heat, CONFIG).stream()
                .collect(Collectors.toMap(ShikabaneHeatScores.Score::entryId, ShikabaneHeatScores.Score::points));
    }

    private static RaceEntry grid(long entryId) {
        RaceEntry e = new RaceEntry();
        e.setEntryId(entryId);
        e.setGridPosition((int) entryId);
        return e;
    }

    private static ResultRow row(int position, long entryId, int laps) {
        return new ResultRow(position, entryId, null, "Driver " + entryId, String.valueOf(entryId), laps,
                laps * 25_000L, 24_000L, null);
    }
}
