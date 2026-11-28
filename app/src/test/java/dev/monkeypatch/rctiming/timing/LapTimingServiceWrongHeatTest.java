package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.query.racecontrol.LapThresholdQuery;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A transponder of a driver in another heat is held back and flagged, not shown as unknown (#856). */
class LapTimingServiceWrongHeatTest {

    private final LiveTimingHub hub = mock(LiveTimingHub.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private final WrongHeatFinder finder = mock(WrongHeatFinder.class);
    private LapTimingService service;

    @BeforeEach
    void setUp() {
        service = new LapTimingService(hub, raceEntryRepository, entryRepository, mock(CompetitorRepository.class),
                mock(LapThresholdQuery.class), mock(RaceClockService.class), mock(ApplicationEventPublisher.class));
        service.setWrongHeatFinder(finder);
        RaceEntry inRace = new RaceEntry();
        inRace.setRaceId(7L);
        inRace.setEntryId(1L);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(List.of(inRace));
        Entry entry = new Entry();
        entry.setId(1L);
        entry.setTransponderNumberSnapshot("1001");
        when(entryRepository.findAllById(any())).thenReturn(List.of(entry));
        when(entryRepository.findById(anyLong())).thenReturn(Optional.of(entry));
    }

    @Test
    void aNumberOfADriverInAnotherHeat_isFlaggedOnceAndNotShownAsUnknown() {
        when(finder.find(7L, "2002")).thenReturn(Optional.of(flag()));

        service.onLapPassing(new LapPassingEvent(7L, "2002", 1_000_000L));
        service.onLapPassing(new LapPassingEvent(7L, "2002", 2_000_000L));

        assertThat(service.wrongHeatsOf(7L)).hasSize(1);
        assertThat(service.wrongHeatsOf(7L).get(0).passings()).isEqualTo(2);
        assertThat(service.wrongHeatsOf(7L).get(0).status()).isEqualTo(WrongHeatDto.Status.OPEN);
        verify(hub).broadcastWrongHeat(eq(7L), any());
        verify(hub, never()).broadcastUnknownTransponder(anyLong(), anyString());
        verify(finder).find(7L, "2002");
    }

    @Test
    void aNumberNobodyHolds_staysUnknown() {
        when(finder.find(7L, "3003")).thenReturn(Optional.empty());

        service.onLapPassing(new LapPassingEvent(7L, "3003", 1_000_000L));

        verify(hub).broadcastUnknownTransponder(7L, "3003");
        verify(hub, never()).broadcastWrongHeat(anyLong(), any());
        assertThat(service.wrongHeatsOf(7L)).isEmpty();
    }

    @Test
    void theWrongHeatPassingsAreNotCounted() {
        when(finder.find(7L, "2002")).thenReturn(Optional.of(flag()));

        service.onLapPassing(new LapPassingEvent(7L, "2002", 1_000_000L));
        service.onLapPassing(new LapPassingEvent(7L, "2002", 60_000_000L));

        assertThat(service.peek(7L).orElseThrow().calculatePositions()).isEmpty();
    }

    @Test
    void aDecisionIsKeptAndBroadcast_andTheFlagIsNotRaisedAgain() {
        when(finder.find(7L, "2002")).thenReturn(Optional.of(flag()));
        service.onLapPassing(new LapPassingEvent(7L, "2002", 1_000_000L));

        service.decideWrongHeat(7L, "2002", WrongHeatDto.Status.DISAPPROVED);
        service.onLapPassing(new LapPassingEvent(7L, "2002", 2_000_000L));

        assertThat(service.wrongHeatOf(7L, "2002").orElseThrow().status()).isEqualTo(WrongHeatDto.Status.DISAPPROVED);
        verify(hub, org.mockito.Mockito.times(2)).broadcastWrongHeat(eq(7L), any());
    }

    private static WrongHeatDto flag() {
        return new WrongHeatDto("2002", 2L, "Sam Ito", 4, "Heat 2", 0, WrongHeatDto.Status.OPEN);
    }
}
