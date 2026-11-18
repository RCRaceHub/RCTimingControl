package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.dto.SectorBoardDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link LiveSectorService}: no database, the entry lookup and split decoders are mocks. */
class LiveSectorServiceTest {

    private static final long RACE = 5L;

    private LapTimingService lapTimingService;
    private LiveTimingHub hub;
    private SplitDecoderRepository splitDecoders;
    private LiveSectorService service;

    @BeforeEach
    void setUp() {
        lapTimingService = mock(LapTimingService.class);
        hub = mock(LiveTimingHub.class);
        splitDecoders = mock(SplitDecoderRepository.class);
        when(splitDecoders.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "A", "h", 5100, 1, 0, true),
                new SplitDecoder(2L, "B", "h", 5100, 2, 0, true)));
        when(lapTimingService.entryOf(RACE, "101")).thenReturn(Optional.of(11L));
        when(lapTimingService.entryOf(RACE, "202")).thenReturn(Optional.of(22L));
        service = new LiveSectorService(lapTimingService, hub, splitDecoders);
    }

    private void lap(String transponder, int lapNumber, long... sectorMs) {
        List<Long> micros = java.util.Arrays.stream(sectorMs).map(ms -> ms * 1000).boxed().toList();
        service.onSectorTimes(new SectorTimesEvent(RACE, transponder, lapNumber, micros));
    }

    private SectorBoardDto lastBroadcast() {
        ArgumentCaptor<SectorBoardDto> captor = ArgumentCaptor.forClass(SectorBoardDto.class);
        verify(hub, org.mockito.Mockito.atLeastOnce()).broadcastSectors(anyLong(), captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    void aLap_isBroadcastWithTheCarsLastAndBestSectors() {
        lap("101", 1, 4_000, 5_000, 6_000);

        SectorBoardDto board = lastBroadcast();
        assertThat(board.sectorCount()).isEqualTo(3);
        assertThat(board.rows()).singleElement().satisfies(row -> {
            assertThat(row.entryId()).isEqualTo(11L);
            assertThat(row.lastMs()).containsExactly(4_000L, 5_000L, 6_000L);
            assertThat(row.bestMs()).containsExactly(4_000L, 5_000L, 6_000L);
        });
    }

    @Test
    void aSlowerLap_keepsTheBestSectorsAndReplacesTheLastOnes() {
        lap("101", 1, 4_000, 5_000, 6_000);
        lap("101", 2, 4_500, 4_900, 6_100);

        var row = lastBroadcast().rows().get(0);
        assertThat(row.lastMs()).containsExactly(4_500L, 4_900L, 6_100L);
        assertThat(row.bestMs()).containsExactly(4_000L, 4_900L, 6_000L);
        assertThat(row.lapNumber()).isEqualTo(2);
    }

    @Test
    void theOverallBestIsTheFastestAnyCarHasSetInEachSector() {
        lap("101", 1, 4_000, 5_000, 6_000);
        lap("202", 1, 4_200, 4_800, 6_300);

        assertThat(lastBroadcast().overallBestMs()).containsExactly(4_000L, 4_800L, 6_000L);
        assertThat(lastBroadcast().rows()).hasSize(2);
    }

    @Test
    void aTransponderWithNoEntry_isDropped() {
        when(lapTimingService.entryOf(RACE, "999")).thenReturn(Optional.empty());

        lap("999", 1, 4_000, 5_000, 6_000);

        verify(hub, never()).broadcastSectors(anyLong(), any());
    }

    @Test
    void anotherRace_startsTheBoardAfresh() {
        lap("101", 1, 4_000, 5_000, 6_000);
        when(lapTimingService.entryOf(6L, "101")).thenReturn(Optional.of(11L));

        service.onSectorTimes(new SectorTimesEvent(6L, "101", 1, List.of(9_000_000L, 9_000_000L, 9_000_000L)));

        assertThat(service.boardOf(RACE).rows()).isEmpty();
        assertThat(service.boardOf(6L).rows()).hasSize(1);
    }

    @Test
    void boardOf_hasNoSectorsWithoutSplitDecoders() {
        when(splitDecoders.findAll()).thenReturn(List.of());

        SectorBoardDto board = service.boardOf(RACE);

        assertThat(board.sectorCount()).isZero();
        assertThat(board.rows()).isEmpty();
    }

    @Test
    void boardOf_countsOnlyEnabledSplitDecoders() {
        when(splitDecoders.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "A", "h", 5100, 1, 0, true),
                new SplitDecoder(2L, "B", "h", 5100, 2, 0, false)));

        assertThat(service.boardOf(RACE).sectorCount()).isEqualTo(2);
    }
}
