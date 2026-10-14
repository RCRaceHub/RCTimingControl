package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.service.BumpUpSeedingService;
import dev.monkeypatch.rctiming.service.ResultSnapshotService;
import dev.monkeypatch.rctiming.service.RoundGeneratorService;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RaceStateMachineServiceTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RoundRepository roundRepository = mock(RoundRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ResultSnapshotService resultSnapshotService = mock(ResultSnapshotService.class);
    private final LiveTimingHub liveTimingHub = mock(LiveTimingHub.class);
    private final LapTimingService lapTimingService = mock(LapTimingService.class);
    private RaceStateMachineService service;

    /** The race as each status event's listeners would read it from the database. */
    private final List<Race> savedWhenPublished = new ArrayList<>();
    private Race lastSaved;

    @BeforeEach
    void setUp() {
        service = new RaceStateMachineService(liveTimingHub, mock(RoundGeneratorService.class), raceRepository,
                lapTimingService, roundRepository, resultSnapshotService, eventPublisher,
                mock(BumpUpSeedingService.class));
        when(raceRepository.save(any())).thenAnswer(inv -> {
            Race race = inv.getArgument(0);
            lastSaved = copy(race);
            return race;
        });
        doAnswer(inv -> savedWhenPublished.add(lastSaved))
                .when(eventPublisher).publishEvent(any(ApplicationEvent.class));
        when(roundRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void invalidTransition_pendingToFinished_throwsAndSavesNothing() {
        Race race = race(RaceStatus.PENDING);
        assertThatThrownBy(() -> service.finish(race))
            .isInstanceOf(IllegalStateTransitionException.class);
        assertThat(race.getFinishedAt()).isNull();
        verify(raceRepository, never()).save(any());
    }

    @Test
    void invalidTransition_finishedToAny_throwsIllegalStateTransitionException() {
        Race race = race(RaceStatus.FINISHED);
        assertThatThrownBy(() -> service.callGrid(race))
            .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> service.start(race))
            .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void callGrid_movesAPendingRaceToGridAndTellsListeners() {
        Race race = race(RaceStatus.PENDING);
        service.callGrid(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.GRID);
        ArgumentCaptor<ApplicationEvent> published = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(RaceStatusChangedEvent.class,
                e -> assertThat(e.getNewStatus()).isEqualTo(RaceStatus.GRID));
        verify(liveTimingHub).broadcastStateChange(7L, RaceStatus.GRID);
    }

    @Test
    void start_savesTheStartTimeBeforeListenersHearOfIt() {
        Race race = race(RaceStatus.GRID);
        service.start(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.RUNNING);
        assertThat(savedWhenPublished).singleElement()
                .satisfies(saved -> assertThat(saved.getStartedAt()).isNotNull());
    }

    @Test
    void resume_keepsTheFirstStartTime() {
        Instant first = Instant.parse("2026-10-18T14:00:00Z");
        Race race = race(RaceStatus.STOPPED);
        race.setStartedAt(first);

        service.start(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.RUNNING);
        assertThat(race.getStartedAt()).isEqualTo(first);
    }

    @Test
    void finish_savesTheFinishTimeBeforeListenersAndTheSnapshot() {
        Race race = race(RaceStatus.RUNNING);
        service.finish(race);

        assertThat(savedWhenPublished).singleElement()
                .satisfies(saved -> assertThat(saved.getFinishedAt()).isNotNull());
        assertThat(race.getAbandonedAt()).isNull();
        verify(resultSnapshotService).snapshot(7L);
    }

    @Test
    void abandon_finishesAndMarksAbandonedAtTheSameMoment() {
        Race race = race(RaceStatus.STOPPED);
        service.abandon(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.FINISHED);
        assertThat(race.getAbandonedAt()).isNotNull().isEqualTo(race.getFinishedAt());
        assertThat(savedWhenPublished).singleElement()
                .satisfies(saved -> assertThat(saved.getAbandonedAt()).isNotNull());
    }

    @Test
    void stop_movesARunningRaceToStoppedAndTellsListeners() {
        Race race = race(RaceStatus.RUNNING);
        service.stop(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.STOPPED);
        assertThat(savedWhenPublished).singleElement()
                .satisfies(saved -> assertThat(saved.getStatus()).isEqualTo(RaceStatus.STOPPED));
        verify(liveTimingHub).broadcastStateChange(7L, RaceStatus.STOPPED);
    }

    @Test
    void finishOrAbandonTwice_isRefusedAndKeepsTheFirstTimes() {
        Instant then = Instant.parse("2026-10-18T14:00:00Z");
        Race race = race(RaceStatus.FINISHED);
        race.setFinishedAt(then);

        assertThatThrownBy(() -> service.finish(race)).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> service.abandon(race)).isInstanceOf(IllegalStateTransitionException.class);

        assertThat(race.getFinishedAt()).isEqualTo(then);
        assertThat(race.getAbandonedAt()).isNull();
        verify(raceRepository, never()).save(any());
    }

    @Test
    void restart_fromAnAbandonedRace_clearsTheTimesAndTheAbandonedMark() {
        Instant then = Instant.parse("2026-10-18T14:00:00Z");
        Race race = race(RaceStatus.FINISHED);
        race.setStartedAt(then);
        race.setFinishedAt(then);
        race.setAbandonedAt(then);

        service.restart(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.PENDING);
        assertThat(race.getStartedAt()).isNull();
        assertThat(race.getFinishedAt()).isNull();
        assertThat(race.getAbandonedAt()).isNull();
        verify(raceRepository).save(race);
        verify(resultSnapshotService).deleteByRaceId(7L);
        verify(lapTimingService).releaseState(7L);
    }

    @Test
    void restart_inATransactionThatRollsBack_keepsTheLiveTiming() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.restart(race(RaceStatus.RUNNING));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(lapTimingService, never()).releaseState(anyLong());
    }

    private static Race race(RaceStatus status) {
        Race race = new Race();
        race.setId(7L);
        race.setRoundId(3L);
        race.setStatus(status);
        return race;
    }

    private static Race copy(Race race) {
        Race copy = race(race.getStatus());
        copy.setStartedAt(race.getStartedAt());
        copy.setFinishedAt(race.getFinishedAt());
        copy.setAbandonedAt(race.getAbandonedAt());
        return copy;
    }
}
