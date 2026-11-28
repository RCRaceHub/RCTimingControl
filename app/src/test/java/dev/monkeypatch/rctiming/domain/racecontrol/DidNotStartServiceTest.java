package dev.monkeypatch.rctiming.domain.racecontrol;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.result.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Marking a car as not started, and taking the mark back. */
class DidNotStartServiceTest {

    private static final long RACE = 7L;
    private static final long ENTRY = 21L;
    private static final long USER = 3L;

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final ResultSnapshotRepository snapshots = mock(ResultSnapshotRepository.class);
    private final ResultSnapshotJson snapshotJson = mock(ResultSnapshotJson.class);
    private final LapTimingService lapTimingService = mock(LapTimingService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private DidNotStartService service;
    private Race race;
    private RaceEntry onGrid;

    @BeforeEach
    void setUp() {
        service = new DidNotStartService(raceRepository, raceEntryRepository, snapshots, snapshotJson,
                lapTimingService, publisher, audit, mock(RaceAuditLabels.class));
        race = new Race();
        race.setId(RACE);
        race.setStatus(RaceStatus.GRID);
        when(raceRepository.getOrThrow(RACE)).thenReturn(race);
        onGrid = new RaceEntry();
        onGrid.setEntryId(ENTRY);
        when(raceEntryRepository.findByRaceIdAndEntryId(RACE, ENTRY)).thenReturn(Optional.of(onGrid));
        when(snapshots.findByRaceId(RACE)).thenReturn(Optional.empty());
        when(lapTimingService.peek(RACE)).thenReturn(Optional.empty());
    }

    @Test
    void marksACarOnTheGridAsNotStarted() {
        service.markDidNotStart(RACE, ENTRY, USER);

        assertThat(onGrid.isDns()).isTrue();
        verify(raceEntryRepository).save(onGrid);
        verify(publisher, never()).publishEvent(any(FinishedRaceCorrected.class));
    }

    @Test
    void refusesACarThatIsAlreadyMarked() {
        onGrid.setDns(true);

        assertThatThrownBy(() -> service.markDidNotStart(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void refusesAnEntryThatIsNotInTheRace() {
        assertThatThrownBy(() -> service.markDidNotStart(RACE, 99L, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void refusesACarThatHasCompletedLapsInAFinishedRace() {
        race.setStatus(RaceStatus.FINISHED);
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setPositionsJson("[]");
        when(snapshots.findByRaceId(RACE)).thenReturn(Optional.of(snapshot));
        when(snapshotJson.positions(RACE, "[]")).thenReturn(List.of(
                new dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow(
                        1, ENTRY, null, "Sam", null, 3, 90_000L, null, null)));

        assertThatThrownBy(() -> service.markDidNotStart(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void sendsTheResultsAgainWhenTheRaceHasFinished() {
        race.setStatus(RaceStatus.FINISHED);

        service.markDidNotStart(RACE, ENTRY, USER);

        verify(publisher).publishEvent(new FinishedRaceCorrected(RACE));
    }

    @Test
    void takesTheMarkBack() {
        onGrid.setDns(true);

        service.undoDidNotStart(RACE, ENTRY, USER);

        assertThat(onGrid.isDns()).isFalse();
        verify(raceEntryRepository).save(onGrid);
    }

    @Test
    void refusesToTakeBackAMarkThatIsNotThere() {
        assertThatThrownBy(() -> service.undoDidNotStart(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void undoOnAFinishedRaceRecordsThatAnAutomaticallyMarkedCarStarted() {
        race.setStatus(RaceStatus.FINISHED);

        service.undoDidNotStart(RACE, ENTRY, USER);

        assertThat(onGrid.isDns()).isFalse();
        assertThat(onGrid.isConfirmedStarted()).isTrue();
        verify(publisher).publishEvent(new FinishedRaceCorrected(RACE));
    }

    @Test
    void markingACarAgainClearsTheConfirmationItStarted() {
        onGrid.setConfirmedStarted(true);

        service.markDidNotStart(RACE, ENTRY, USER);

        assertThat(onGrid.isDns()).isTrue();
        assertThat(onGrid.isConfirmedStarted()).isFalse();
    }
}
