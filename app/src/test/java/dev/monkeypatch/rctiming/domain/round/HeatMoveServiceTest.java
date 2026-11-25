package dev.monkeypatch.rctiming.domain.round;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HeatMoveServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private RaceEntryRepository raceEntryRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private EntryRepository entryRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AuditService audit;

    private HeatMoveService service;
    private Race heat1;
    private Race heat2;
    private final List<RaceEntry> inHeat1 = new ArrayList<>();
    private final List<RaceEntry> inHeat2 = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new HeatMoveService(raceRepository, raceEntryRepository, roundRepository, entryRepository, audit);
        heat1 = race(1L, 1);
        heat2 = race(2L, 2);
        Round round = new Round();
        round.setId(9L);
        round.setEventId(3L);
        lenient().when(raceRepository.getOrThrow(1L)).thenReturn(heat1);
        lenient().when(raceRepository.getOrThrow(2L)).thenReturn(heat2);
        lenient().when(roundRepository.getOrThrow(9L)).thenReturn(round);
        for (long e = 11; e <= 13; e++) inHeat1.add(raceEntry(1L, e, (int) (e - 10)));
        inHeat2.add(raceEntry(2L, 21L, 1));
        lenient().when(raceEntryRepository.findByRaceIdAndEntryId(any(), any())).thenAnswer(inv -> {
            long raceId = inv.getArgument(0);
            long entryId = inv.getArgument(1);
            return inRace(raceId).stream().filter(r -> r.getEntryId() == entryId).findFirst();
        });
        lenient().when(raceEntryRepository.findByRaceIdOrderByGridPosition(any()))
                .thenAnswer(inv -> inRace(inv.getArgument(0)));
    }

    @Test
    void movingADriverAppendsThemToTheTargetAndClosesTheGapInTheSource() {
        service.moveEntry(Actor.system("test"), 1L, 11L, 2L, false);

        RaceEntry moved = inHeat1.get(0);
        assertThat(moved.getRaceId()).isEqualTo(2L);
        assertThat(moved.getGridPosition()).isEqualTo(2);
        // Car 1 is taken in the target heat, so the driver takes the next free number there
        assertThat(moved.getCarNumber()).isEqualTo(2);
        assertThat(inHeat1.get(1).getGridPosition()).isEqualTo(1);
        assertThat(inHeat1.get(2).getGridPosition()).isEqualTo(2);
        // Nobody else is renumbered
        assertThat(inHeat1.get(1).getCarNumber()).isEqualTo(2);
        assertThat(inHeat1.get(2).getCarNumber()).isEqualTo(3);
    }

    @Test
    void aMovedDriverKeepsTheirCarNumberWhenTheTargetHeatDoesNotUseIt() {
        inHeat2.get(0).setCarNumber(7);

        service.moveEntry(Actor.system("test"), 1L, 11L, 2L, false);

        assertThat(inHeat1.get(0).getCarNumber()).isEqualTo(1);
        assertThat(inHeat2.get(0).getCarNumber()).isEqualTo(7);
    }

    @Test
    void aHeatThatHasBeenCalledIsRefused() {
        heat2.setStatus(RaceStatus.GRID);

        assertThatThrownBy(() -> service.moveEntry(Actor.system("test"), 1L, 11L, 2L, false))
                .isInstanceOf(StateConflictException.class);
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void aHeatOfAnotherClassIsRefused() {
        heat2.setEventClassId(99L);

        assertThatThrownBy(() -> service.moveEntry(Actor.system("test"), 1L, 11L, 2L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same class");
    }

    @Test
    void theOnlyDriverInAHeatIsNotMovedOut() {
        inHeat1.remove(2);
        inHeat1.remove(1);

        assertThatThrownBy(() -> service.moveEntry(Actor.system("test"), 1L, 11L, 2L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abandon");
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void aDriverNotInTheHeatIsNotFound() {
        assertThatThrownBy(() -> service.moveEntry(Actor.system("test"), 1L, 21L, 2L, false))
                .hasMessageContaining("not in race");
    }

    @Test
    void aLateEntryIsAddedAtTheBackOfTheHeatWithTheNextFreeCarNumber() {
        lateEntry(31L, EntryStatus.CONFIRMED);

        service.addEntry(Actor.system("test"), 1L, 31L, false);

        ArgumentCaptor<RaceEntry> saved = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository).save(saved.capture());
        assertThat(saved.getValue().getRaceId()).isEqualTo(1L);
        assertThat(saved.getValue().getEntryId()).isEqualTo(31L);
        assertThat(saved.getValue().getGridPosition()).isEqualTo(4);
        assertThat(saved.getValue().getCarNumber()).isEqualTo(4);
        // Nobody else is renumbered
        assertThat(inHeat1.get(0).getCarNumber()).isEqualTo(1);
    }

    @Test
    void aLateEntryIsRefusedIntoAHeatThatHasBeenCalled() {
        lateEntry(31L, EntryStatus.CONFIRMED);
        heat1.setStatus(RaceStatus.GRID);

        assertThatThrownBy(() -> service.addEntry(Actor.system("test"), 1L, 31L, false))
                .isInstanceOf(StateConflictException.class);
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void aWithdrawnEntryIsNotAddedToAHeat() {
        lateEntry(31L, EntryStatus.WITHDRAWN);

        assertThatThrownBy(() -> service.addEntry(Actor.system("test"), 1L, 31L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmed");
    }

    @Test
    void anEntryOfAnotherClassIsNotAddedToAHeat() {
        lateEntry(31L, EntryStatus.CONFIRMED).setEventClassId(99L);

        assertThatThrownBy(() -> service.addEntry(Actor.system("test"), 1L, 31L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own class");
    }

    @Test
    void anEntryAlreadyInAHeatOfTheRoundIsNotAddedAgain() {
        lateEntry(11L, EntryStatus.CONFIRMED);
        org.mockito.Mockito.when(raceRepository.findByRoundIdOrderBySequenceInRound(9L))
                .thenReturn(List.of(heat1, heat2));

        assertThatThrownBy(() -> service.addEntry(Actor.system("test"), 2L, 11L, false))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("already in a heat");
    }

    @Test
    void withAllRoundsALateEntryJoinsTheSameHeatOfLaterRoundsThatAreStillPendingAndSkipsRoundsAlreadyRun() {
        lateEntry(31L, EntryStatus.CONFIRMED);
        Round round2 = round(10L, 2);
        Round round3 = round(11L, 3);
        Round round1 = round(9L, 1);
        Race r2heat1 = race(5L, 1);
        r2heat1.setRoundId(10L);
        Race r3heat1 = race(6L, 1);
        r3heat1.setRoundId(11L);
        r3heat1.setStatus(RaceStatus.FINISHED);
        org.mockito.Mockito.when(roundRepository.findByEventIdOrderBySequenceInEvent(3L))
                .thenReturn(List.of(round1, round2, round3));
        lenient().when(raceRepository.findByRoundIdOrderBySequenceInRound(9L)).thenReturn(List.of(heat1, heat2));
        org.mockito.Mockito.when(raceRepository.findByRoundIdOrderBySequenceInRound(10L)).thenReturn(List.of(r2heat1));
        org.mockito.Mockito.when(raceRepository.findByRoundIdOrderBySequenceInRound(11L)).thenReturn(List.of(r3heat1));

        service.addEntry(Actor.system("test"), 1L, 31L, true);

        ArgumentCaptor<RaceEntry> saved = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(RaceEntry::getRaceId).containsExactly(1L, 5L);
        // The same car number in each round, since it is free there
        assertThat(saved.getAllValues()).extracting(RaceEntry::getCarNumber).containsExactly(4, 4);
    }

    private Entry lateEntry(long id, EntryStatus status) {
        Entry entry = new Entry();
        entry.setId(id);
        entry.setEventId(3L);
        entry.setEventClassId(7L);
        entry.setStatus(status);
        lenient().when(entryRepository.getOrThrow(id)).thenReturn(entry);
        return entry;
    }

    private static Round round(long id, int sequence) {
        Round round = new Round();
        round.setId(id);
        round.setEventId(3L);
        round.setSequenceInEvent(sequence);
        return round;
    }

    /** The rows now in a race, as the repository would find them after a move changed a row's race. */
    private List<RaceEntry> inRace(long raceId) {
        List<RaceEntry> rows = new ArrayList<>();
        for (RaceEntry r : inHeat1) if (r.getRaceId() == raceId) rows.add(r);
        for (RaceEntry r : inHeat2) if (r.getRaceId() == raceId) rows.add(r);
        return rows;
    }

    private static Race race(long id, int heatNumber) {
        Race race = new Race();
        race.setId(id);
        race.setRoundId(9L);
        race.setEventClassId(7L);
        race.setHeatNumber(heatNumber);
        race.setStatus(RaceStatus.PENDING);
        return race;
    }

    private static RaceEntry raceEntry(long raceId, long entryId, int position) {
        RaceEntry e = new RaceEntry();
        e.setRaceId(raceId);
        e.setEntryId(entryId);
        e.setGridPosition(position);
        e.setCarNumber(position);
        return e;
    }
}
