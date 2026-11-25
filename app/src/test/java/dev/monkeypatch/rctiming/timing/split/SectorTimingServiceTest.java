package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link SectorTimingService}: no database, the split decoders come from a mocked repository. */
class SectorTimingServiceTest {

    private static final long RACE = 5L;
    private static final long SECOND = 1_000_000L;

    private SplitDecoderRepository repository;
    private ApplicationEventPublisher publisher;
    private ModuleSwitch moduleSwitch;
    private SectorTimingService service;

    @BeforeEach
    void setUp() {
        repository = mock(SplitDecoderRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        when(repository.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "Back straight", "10.0.0.2", 5100, 1, 0, true),
                new SplitDecoder(2L, "Hairpin", "10.0.0.3", 5100, 2, 0, true)));
        moduleSwitch = mock(ModuleSwitch.class);
        when(moduleSwitch.isEnabled(SplitTimingModule.ID)).thenReturn(true);
        service = new SectorTimingService(repository, publisher, moduleSwitch);
    }

    private void finish(String transponder, long seconds) {
        service.onFinishCrossing(new LapPassingEvent(RACE, transponder, seconds * SECOND));
    }

    private void split(int number, String transponder, long seconds) {
        service.onSplitCrossing(new SplitPassingEvent(RACE, number, number, transponder, seconds * SECOND));
    }

    @Test
    void lapWithEverySplit_publishesSectorTimesThatAddUpToTheLap() {
        finish("101", 100);
        split(1, "101", 104);
        split(2, "101", 109);
        finish("101", 115);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sectorMicros()).containsExactly(4 * SECOND, 5 * SECOND, 6 * SECOND);
        assertThat(captor.getValue().lapNumber()).isEqualTo(1);
        assertThat(captor.getValue().transponderNumber()).isEqualTo("101");
    }

    @Test
    void lapMissingASplit_publishesNothing() {
        finish("101", 100);
        split(2, "101", 109);
        finish("101", 115);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void splitsOutOfOrder_publishNothing() {
        finish("101", 100);
        split(2, "101", 104);
        split(1, "101", 109);
        finish("101", 115);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aMissedLapDoesNotSpoilTheNextOne() {
        finish("101", 100);
        finish("101", 115);
        split(1, "101", 119);
        split(2, "101", 124);
        finish("101", 130);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().lapNumber()).isEqualTo(2);
        assertThat(captor.getValue().sectorMicros()).containsExactly(4 * SECOND, 5 * SECOND, 6 * SECOND);
    }

    @Test
    void aDoubleReadOfTheFinishLoop_isIgnored() {
        finish("101", 100);
        split(1, "101", 104);
        split(2, "101", 109);
        finish("101", 101);
        finish("101", 115);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sectorMicros()).hasSize(3);
    }

    @Test
    void aSecondReadOfTheSameSplit_keepsTheFirst() {
        finish("101", 100);
        split(1, "101", 104);
        split(1, "101", 105);
        split(2, "101", 109);
        finish("101", 115);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sectorMicros()).containsExactly(4 * SECOND, 5 * SECOND, 6 * SECOND);
    }

    @Test
    void noRunningRace_isIgnored() {
        service.onFinishCrossing(new LapPassingEvent(LapPassingEvent.NO_RACE, "101", 100 * SECOND));
        service.onSplitCrossing(new SplitPassingEvent(LapPassingEvent.NO_RACE, 1, 1, "101", 104 * SECOND));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void noSplitDecoders_meansNoSectorTimes() {
        when(repository.findAll()).thenReturn(List.of());

        finish("101", 100);
        finish("101", 115);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDisabledDecoder_isNotExpected() {
        when(repository.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "Back straight", "10.0.0.2", 5100, 1, 0, true),
                new SplitDecoder(2L, "Hairpin", "10.0.0.3", 5100, 2, 0, false)));

        finish("101", 100);
        split(1, "101", 104);
        finish("101", 115);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sectorMicros()).containsExactly(4 * SECOND, 11 * SECOND);
    }

    @Test
    void aChangeToTheSplitDecoders_forgetsLapsMeasuredAgainstTheOldSplits() {
        finish("101", 100);
        split(1, "101", 104);
        split(2, "101", 109);
        when(repository.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "Back straight", "10.0.0.2", 5100, 1, 0, true)));

        service.onDecodersChanged(new SplitDecodersChangedEvent());
        finish("101", 115);
        // The first crossing after the change only starts a lap again
        verify(publisher, never()).publishEvent(any(Object.class));

        split(1, "101", 119);
        finish("101", 130);

        ArgumentCaptor<SectorTimesEvent> captor = ArgumentCaptor.forClass(SectorTimesEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sectorMicros()).containsExactly(4 * SECOND, 11 * SECOND);
    }

    @Test
    void doesNothingWhileTheModuleIsOff() {
        when(moduleSwitch.isEnabled(SplitTimingModule.ID)).thenReturn(false);

        finish("101", 0);
        finish("101", 60);

        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
