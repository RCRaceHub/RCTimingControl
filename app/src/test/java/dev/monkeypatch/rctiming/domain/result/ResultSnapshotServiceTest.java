package dev.monkeypatch.rctiming.domain.result;

import dev.monkeypatch.rctiming.domain.round.FinalsMarshalPenalties;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustmentRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** When a finished race's live timing is let go (#217). */
class ResultSnapshotServiceTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final LapTimingService lapTimingService = mock(LapTimingService.class);
    private final RoundRepository roundRepository = mock(RoundRepository.class);
    private final ResultSnapshotRepository snapshots = mock(ResultSnapshotRepository.class);
    private final ResultSnapshotJson snapshotJson = mock(ResultSnapshotJson.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private ResultSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new ResultSnapshotService(raceRepository, roundRepository, lapTimingService,
                snapshots, snapshotJson, raceEntryRepository, entryRepository, mock(CompetitorRepository.class),
                mock(PenaltyRepository.class), mock(MarshalAdjustmentRepository.class),
                mock(FinalsMarshalPenalties.class), mock(RaceEliminationRepository.class));
        Race race = new Race();
        race.setId(7L);
        race.setRoundId(3L);
        race.setStatus(RaceStatus.FINISHED);
        race.setStartedAt(Instant.parse("2026-10-18T14:00:00Z"));
        race.setFinishedAt(Instant.parse("2026-10-18T14:05:00Z"));
        when(raceRepository.getOrThrow(7L)).thenReturn(race);
        when(lapTimingService.peek(7L)).thenReturn(Optional.of(new LiveRaceState()));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void snapshot_releasesTheLiveTimingOnlyOnceTheFinishCommits() {
        service.snapshot(7L);
        verify(lapTimingService, never()).releaseState(anyLong());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(lapTimingService).releaseState(7L);
    }

    @Test
    void snapshot_inAFinishThatRollsBack_keepsTheLiveTiming() {
        service.snapshot(7L);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(lapTimingService, never()).releaseState(anyLong());
    }

    private void gridOf(long... entryIds) {
        List<RaceEntry> grid = new ArrayList<>();
        for (long id : entryIds) {
            RaceEntry re = new RaceEntry();
            re.setEntryId(id);
            grid.add(re);
        }
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(grid);
    }

    private List<ResultRow> correctedRowsFor(RoundType type, List<ResultRow> timed) {
        Round round = new Round();
        round.setType(type);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(round));
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setTimedPositionsJson("timed");
        snapshot.setFinishedAt(Instant.parse("2026-10-18T14:05:00Z"));
        when(snapshots.findByRaceId(7L)).thenReturn(Optional.of(snapshot));
        when(snapshotJson.positions(7L, "timed")).thenReturn(timed);
        service.applyCorrections(7L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResultRow>> written = ArgumentCaptor.forClass(List.class);
        verify(snapshotJson, atLeastOnce()).write(eq(7L), written.capture());
        return written.getAllValues().get(written.getAllValues().size() - 1);
    }

    private static ResultRow ran(long entryId) {
        return new ResultRow(1, entryId, null, "Driver " + entryId, null, 5, 100_000L, 20_000L, null);
    }

    @Test
    void aQualifyingCarThatNeverCrossedTheLineIsListedLastAsDidNotStart() {
        gridOf(1L, 2L, 3L);
        Entry withdrawn = new Entry();
        withdrawn.setStatus(EntryStatus.WITHDRAWN);
        when(entryRepository.findById(3L)).thenReturn(Optional.of(withdrawn));
        when(entryRepository.findById(2L)).thenReturn(Optional.of(new Entry()));

        List<ResultRow> rows = correctedRowsFor(RoundType.QUALIFIER, List.of(ran(1L)));

        assertThat(rows).extracting(ResultRow::entryId).containsExactly(1L, 2L);
        assertThat(rows.get(1).dns()).isTrue();
        assertThat(rows.get(1).lapsCompleted()).isZero();
        assertThat(rows.get(0).dns()).isFalse();
    }

    @Test
    void aFinalNeverMarksAnUnseenCarAsDidNotStart() {
        gridOf(1L, 2L);

        List<ResultRow> rows = correctedRowsFor(RoundType.FINAL, List.of(ran(1L)));

        assertThat(rows).extracting(ResultRow::entryId).containsExactly(1L);
    }

    @Test
    void aHeatWithNoCarTimedAtAllMarksNobody() {
        gridOf(1L, 2L);

        List<ResultRow> rows = correctedRowsFor(RoundType.QUALIFIER, List.of());

        assertThat(rows).isEmpty();
    }

    @Test
    void aCarAnOfficialConfirmedStartedIsNotMarkedAutomatically() {
        RaceEntry confirmed = new RaceEntry();
        confirmed.setEntryId(2L);
        confirmed.setConfirmedStarted(true);
        RaceEntry ran = new RaceEntry();
        ran.setEntryId(1L);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(List.of(ran, confirmed));

        List<ResultRow> rows = correctedRowsFor(RoundType.QUALIFIER, List.of(ran(1L)));

        assertThat(rows).extracting(ResultRow::entryId).containsExactly(1L);
    }

    @Test
    void anAbandonedHeatMarksNobodyAutomatically() {
        gridOf(1L, 2L);
        raceRepository.getOrThrow(7L).setAbandonedAt(Instant.parse("2026-10-18T14:04:00Z"));

        List<ResultRow> rows = correctedRowsFor(RoundType.QUALIFIER, List.of(ran(1L)));

        assertThat(rows).extracting(ResultRow::entryId).containsExactly(1L);
    }

    @Test
    void aHeatWhereNoTimedCarCompletedALapMarksNobodyAutomatically() {
        gridOf(1L, 2L);

        List<ResultRow> rows = correctedRowsFor(RoundType.QUALIFIER, List.of(
                new ResultRow(1, 1L, null, "Driver 1", null, 0, 0L, null, null)));

        assertThat(rows).extracting(ResultRow::entryId).containsExactly(1L);
    }
}
