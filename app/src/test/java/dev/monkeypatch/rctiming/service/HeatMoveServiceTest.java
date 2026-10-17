package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HeatMoveServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private RaceEntryRepository raceEntryRepository;
    @Mock private RoundRepository roundRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AuditService audit;

    private HeatMoveService service;
    private Race heat1;
    private Race heat2;
    private final List<RaceEntry> inHeat1 = new ArrayList<>();
    private final List<RaceEntry> inHeat2 = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new HeatMoveService(raceRepository, raceEntryRepository, roundRepository, audit);
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
        assertThat(moved.getCarNumber()).isEqualTo(2);
        assertThat(inHeat1.get(1).getGridPosition()).isEqualTo(1);
        assertThat(inHeat1.get(1).getCarNumber()).isEqualTo(1);
        assertThat(inHeat1.get(2).getGridPosition()).isEqualTo(2);
        assertThat(inHeat1.get(2).getCarNumber()).isEqualTo(2);
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
    void aDriverNotInTheHeatIsNotFound() {
        assertThatThrownBy(() -> service.moveEntry(Actor.system("test"), 1L, 21L, 2L, false))
                .hasMessageContaining("not in race");
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
