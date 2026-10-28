package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.domain.event.IllegalStateTransitionException;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.StartType;
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
    private final RoundGeneratorService roundGeneratorService = mock(RoundGeneratorService.class);
    private final RaceFormatService raceFormatService = mock(RaceFormatService.class);
    private final EventClassRepository eventClassRepository = mock(EventClassRepository.class);
    private RaceStateMachineService service;

    /** The race as each status event's listeners would read it from the database. */
    private final List<Race> savedWhenPublished = new ArrayList<>();
    private Race lastSaved;

    @BeforeEach
    void setUp() {
        service = new RaceStateMachineService(liveTimingHub, roundGeneratorService, raceRepository,
                lapTimingService, roundRepository, resultSnapshotService, eventPublisher,
                mock(BumpUpSeedingService.class), raceFormatService,
                eventClassRepository, mock(RaceEliminationRepository.class), mock(RacePointPenaltyRepository.class),
                mock(dev.monkeypatch.rctiming.service.ShikabaneGridService.class));
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
    void callGrid_takesTheStartTypeFromTheFormatAsItIsNow() {
        Round practiceRound = new Round();
        practiceRound.setType(RoundType.PRACTICE);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(practiceRound));
        EventClass eventClass = new EventClass();
        when(eventClassRepository.findById(5L)).thenReturn(Optional.of(eventClass));
        when(raceFormatService.startType(eventClass, RoundType.PRACTICE)).thenReturn(StartType.STAGGER);
        Race race = race(RaceStatus.PENDING);
        race.setEventClassId(5L);
        race.setStartType(StartType.ROLLING);

        service.callGrid(race);

        assertThat(race.getStartType()).isEqualTo(StartType.STAGGER);
        assertThat(lastSaved.getStartType()).isEqualTo(StartType.STAGGER);
    }

    @Test
    void callGrid_keepsTheStartTypeOfARaceWithNoEventClass() {
        Round practiceRound = new Round();
        practiceRound.setType(RoundType.PRACTICE);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(practiceRound));
        Race race = race(RaceStatus.PENDING);
        race.setStartType(StartType.ROLLING);

        service.callGrid(race);

        assertThat(race.getStartType()).isEqualTo(StartType.ROLLING);
        verify(raceFormatService, never()).startType(any(), any());
    }

    @Test
    void finishingPractice_doesNotSetTheQualifyingOrder() {
        Race race = finishedHeat(RoundType.PRACTICE, RoundType.QUALIFIER);

        service.finish(race);

        verify(roundGeneratorService, never()).applyPreviousRoundFinishingOrder(anyLong(), any());
    }

    @Test
    void finishingPractice_setsTheNextPracticeOrderFromTheFinishingOrder() {
        Race race = finishedHeat(RoundType.PRACTICE, RoundType.PRACTICE);

        service.finish(race);

        verify(roundGeneratorService).applyPreviousRoundFinishingOrder(8L, List.of(42L));
    }

    @Test
    void finishingAQualifier_setsTheNextQualifyingOrderFromTheFinishingOrder() {
        Race race = finishedHeat(RoundType.QUALIFIER, RoundType.QUALIFIER);

        service.finish(race);

        verify(roundGeneratorService).applyPreviousRoundFinishingOrder(8L, List.of(42L));
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
    void final_cannotBeCalledOrStartedWhileAQualifyingHeatIsUnfinished() {
        Race finalRace = finalRace(RaceStatus.PENDING);
        when(raceRepository.findByEventClassIdAndRoundType(5L, RoundType.QUALIFIER))
                .thenReturn(List.of(qualifier(RaceStatus.FINISHED), qualifier(RaceStatus.PENDING)));

        assertThatThrownBy(() -> service.callGrid(finalRace)).isInstanceOf(IllegalStateTransitionException.class);
        finalRace.setStatus(RaceStatus.GRID);
        assertThatThrownBy(() -> service.start(finalRace)).isInstanceOf(IllegalStateTransitionException.class);
        assertThat(finalRace.getStatus()).isEqualTo(RaceStatus.GRID);
        verify(raceRepository, never()).save(any());
    }

    @Test
    void final_canStartOnceEveryQualifyingHeatIsFinished() {
        Race finalRace = finalRace(RaceStatus.GRID);
        when(raceRepository.findByEventClassIdAndRoundType(5L, RoundType.QUALIFIER))
                .thenReturn(List.of(qualifier(RaceStatus.FINISHED), qualifier(RaceStatus.FINISHED)));

        service.start(finalRace);

        assertThat(finalRace.getStatus()).isEqualTo(RaceStatus.RUNNING);
    }

    @Test
    void qualifier_isNotHeldUpByOtherQualifyingHeats() {
        Round qualifierRound = new Round();
        qualifierRound.setType(RoundType.QUALIFIER);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(qualifierRound));

        service.callGrid(race(RaceStatus.PENDING));

        verify(raceRepository, never()).findByEventClassIdAndRoundType(any(), any());
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
    void abandon_worksOnARaceThatNeverStarted() {
        Race race = race(RaceStatus.PENDING);
        service.abandon(race);

        assertThat(race.getStatus()).isEqualTo(RaceStatus.FINISHED);
        assertThat(race.getAbandonedAt()).isNotNull();
        assertThat(race.getStartedAt()).isNull();
    }

    @Test
    void abandon_refusesAFinishedRace() {
        assertThatThrownBy(() -> service.abandon(race(RaceStatus.FINISHED)))
            .isInstanceOf(IllegalStateTransitionException.class);
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

    /** A running heat 1 of the given round type, with a car in the finishing order and a heat 1 in the next round. */
    private Race finishedHeat(RoundType type, RoundType nextType) {
        Round round = new Round();
        round.setId(3L);
        round.setEventId(1L);
        round.setSequenceInEvent(1);
        round.setType(type);
        Round next = new Round();
        next.setId(4L);
        next.setEventId(1L);
        next.setSequenceInEvent(2);
        next.setType(nextType);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(round));
        when(roundRepository.findByEventIdOrderBySequenceInEvent(1L)).thenReturn(List.of(round, next));
        Race nextHeat = new Race();
        nextHeat.setId(8L);
        nextHeat.setEventClassId(5L);
        nextHeat.setHeatNumber(1);
        when(raceRepository.findByRoundIdOrderBySequenceInRound(4L)).thenReturn(List.of(nextHeat));
        when(resultSnapshotService.finishingOrder(7L)).thenReturn(List.of(42L));
        Race race = race(RaceStatus.RUNNING);
        race.setEventClassId(5L);
        race.setHeatNumber(1);
        return race;
    }

    private static Race race(RaceStatus status) {
        Race race = new Race();
        race.setId(7L);
        race.setRoundId(3L);
        race.setStatus(status);
        return race;
    }

    private Race finalRace(RaceStatus status) {
        Round finalRound = new Round();
        finalRound.setType(RoundType.FINAL);
        when(roundRepository.findById(3L)).thenReturn(Optional.of(finalRound));
        Race race = race(status);
        race.setEventClassId(5L);
        return race;
    }

    private static Race qualifier(RaceStatus status) {
        Race race = race(status);
        race.setId(null);
        return race;
    }

    private static Race copy(Race race) {
        Race copy = race(race.getStatus());
        copy.setStartedAt(race.getStartedAt());
        copy.setFinishedAt(race.getFinishedAt());
        copy.setAbandonedAt(race.getAbandonedAt());
        copy.setStartType(race.getStartType());
        return copy;
    }
}
