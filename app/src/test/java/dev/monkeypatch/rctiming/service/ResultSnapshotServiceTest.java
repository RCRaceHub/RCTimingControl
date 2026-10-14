package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustmentRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** When a finished race's live timing is let go (#217). */
class ResultSnapshotServiceTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final LapTimingService lapTimingService = mock(LapTimingService.class);
    private ResultSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new ResultSnapshotService(raceRepository, mock(RoundRepository.class), lapTimingService,
                mock(ResultSnapshotRepository.class), mock(ResultSnapshotJson.class),
                mock(RaceEntryRepository.class), mock(EntryRepository.class), mock(CompetitorRepository.class),
                mock(PenaltyRepository.class), mock(MarshalAdjustmentRepository.class));
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
}
