package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.format.TimedRaceConfig;
import dev.monkeypatch.rctiming.domain.format.QualifyingType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceElimination;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
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

/** Marking eliminations and revivals by hand in a Shikabane race. */
class RaceEliminationServiceTest {

    private static final long RACE = 7L;
    private static final long ENTRY = 21L;
    private static final long USER = 3L;

    private final RaceEliminationRepository eliminations = mock(RaceEliminationRepository.class);
    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final EventClassRepository eventClassRepository = mock(EventClassRepository.class);
    private final RaceFormatService raceFormatService = mock(RaceFormatService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AuditService audit = mock(AuditService.class, Answers.RETURNS_DEEP_STUBS);
    private RaceEliminationService service;
    private Race race;
    private final EventClass eventClass = new EventClass();

    @BeforeEach
    void setUp() {
        service = new RaceEliminationService(eliminations, raceRepository, raceEntryRepository, eventClassRepository,
                raceFormatService, publisher, audit, mock(RaceAuditLabels.class));
        race = new Race();
        race.setId(RACE);
        race.setEventClassId(5L);
        race.setStatus(RaceStatus.RUNNING);
        when(raceRepository.getOrThrow(RACE)).thenReturn(race);
        eventClass.setConfigSnapshot(new ShikabaneConfig(5, StartType.GRID, 3, 3, 1));
        when(eventClassRepository.findById(5L)).thenReturn(Optional.of(eventClass));
        when(raceFormatService.getEffectiveConfig(eventClass)).thenReturn(eventClass.getConfigSnapshot());
        when(raceEntryRepository.findByRaceIdAndEntryId(RACE, ENTRY)).thenReturn(Optional.of(new RaceEntry()));
        when(eliminations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void eliminatingARunningCarSavesTheRow() {
        RaceElimination saved = service.eliminate(RACE, ENTRY, USER);

        assertThat(saved.getRaceId()).isEqualTo(RACE);
        assertThat(saved.getEntryId()).isEqualTo(ENTRY);
        assertThat(saved.getEliminatedBy()).isEqualTo(USER);
        assertThat(saved.isActive()).isTrue();
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aCarAlreadyDeadCannotBeEliminatedAgain() {
        when(eliminations.findActive(RACE, ENTRY)).thenReturn(Optional.of(new RaceElimination()));

        assertThatThrownBy(() -> service.eliminate(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void onlyAShikabaneRaceTakesEliminations() {
        TimedRaceConfig timed = new TimedRaceConfig(5, StartType.GRID, QualifyingType.ROUND_BY_ROUND, 0, 1);
        eventClass.setConfigSnapshot(timed);
        when(raceFormatService.getEffectiveConfig(eventClass)).thenReturn(timed);

        assertThatThrownBy(() -> service.eliminate(RACE, ENTRY, USER))
                .isInstanceOf(StateConflictException.class).hasMessageContaining("Shikabane");
        assertThat(service.isShikabane(RACE)).isFalse();
    }

    @Test
    void aRaceThatHasNotStartedTakesNone() {
        race.setStatus(RaceStatus.GRID);

        assertThatThrownBy(() -> service.eliminate(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void anEntryNotInTheRaceIsRefused() {
        when(raceEntryRepository.findByRaceIdAndEntryId(RACE, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminate(RACE, 99L, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void reviveKeepsTheRowAsARevival() {
        RaceElimination dead = new RaceElimination();
        dead.setRaceId(RACE);
        dead.setEntryId(ENTRY);
        when(eliminations.findActive(RACE, ENTRY)).thenReturn(Optional.of(dead));

        RaceElimination revived = service.revive(RACE, ENTRY, USER);

        assertThat(revived.isActive()).isFalse();
        assertThat(revived.getRevivedBy()).isEqualTo(USER);
    }

    @Test
    void reviveNeedsADeadCar() {
        assertThatThrownBy(() -> service.revive(RACE, ENTRY, USER)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void undoDeletesTheRowWithNoRevival() {
        RaceElimination dead = new RaceElimination();
        when(eliminations.findActive(RACE, ENTRY)).thenReturn(Optional.of(dead));

        service.undoElimination(RACE, ENTRY, USER);

        verify(eliminations).delete(dead);
    }

    @Test
    void markingAFinishedRaceSendsItsResultAgain() {
        race.setStatus(RaceStatus.FINISHED);

        service.eliminate(RACE, ENTRY, USER);

        verify(publisher).publishEvent(new FinishedRaceCorrected(RACE));
    }

    @Test
    void deadInOrderSkipsRevivals() {
        RaceElimination first = new RaceElimination();
        first.setEntryId(1L);
        RaceElimination revived = new RaceElimination();
        revived.setEntryId(2L);
        revived.setRevivedAt(java.time.Instant.now());
        RaceElimination third = new RaceElimination();
        third.setEntryId(3L);
        when(eliminations.findByRaceId(RACE)).thenReturn(List.of(first, revived, third));

        assertThat(service.deadInOrder(RACE)).containsExactly(1L, 3L);
    }
}
