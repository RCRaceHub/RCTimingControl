package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.decoderprotocol.timing.AmbRc4TimingSource;
import dev.monkeypatch.rctiming.decoderprotocol.timing.EpochCorrectedPassing;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SplitLoopListener} that never open a socket: the source factory records each source's
 * callbacks so a test can drive them, as in {@code DecoderListenerTest}. A decoder with a clock offset is how a second
 * decoder is faked without a second decoder.
 */
class SplitLoopListenerTest {

    private SplitDecoderRepository repository;
    private RaceRepository raceRepository;
    private ApplicationEventPublisher publisher;
    private SplitLoopListener listener;

    private final List<String> created = new ArrayList<>();
    private final List<Consumer<EpochCorrectedPassing>> passingCallbacks = new ArrayList<>();
    private final List<Consumer<AmbRc4TimingSource.ConnectionState>> statusCallbacks = new ArrayList<>();
    private final List<AmbRc4TimingSource> sources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(SplitDecoderRepository.class);
        raceRepository = mock(RaceRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        listener = new SplitLoopListener(repository, raceRepository, publisher, Runnable::run,
                (host, port, onPassing, onStatus) -> {
                    created.add(host + ":" + port);
                    passingCallbacks.add(onPassing);
                    statusCallbacks.add(onStatus);
                    AmbRc4TimingSource source = mock(AmbRc4TimingSource.class);
                    sources.add(source);
                    return source;
                });
    }

    private static EpochCorrectedPassing passing(String transponder, long micros) {
        return new EpochCorrectedPassing(transponder, micros, 1, 1, 63, 1);
    }

    @Test
    void start_connectsEveryEnabledDecoderAndSkipsDisabledOnes() {
        when(repository.findAll()).thenReturn(List.of(
                new SplitDecoder(1L, "A", "10.0.0.2", 5100, 1, 0, true),
                new SplitDecoder(2L, "B", "10.0.0.3", 5101, 2, 0, false),
                new SplitDecoder(3L, "C", "10.0.0.4", 5102, 3, 0, true)));

        listener.start();

        assertThat(created).containsExactly("10.0.0.2:5100", "10.0.0.4:5102");
        verify(sources.get(0)).start();
        verify(sources.get(1)).start();
    }

    @Test
    void aPassing_isPublishedAsASplitPassingShiftedByTheClockOffset() {
        when(repository.findAll()).thenReturn(List.of(new SplitDecoder(7L, "A", "h", 5100, 2, -20, true)));
        Race race = mock(Race.class);
        when(race.getId()).thenReturn(9L);
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.of(race));
        listener.start();

        passingCallbacks.get(0).accept(passing("1234567", 100_000_000L));

        verify(publisher).publishEvent(new SplitPassingEvent(9L, 7L, 2, "1234567", 100_000_000L - 20_000L));
    }

    @Test
    void aPassing_neverBecomesALapPassing() {
        when(repository.findAll()).thenReturn(List.of(new SplitDecoder(7L, "A", "h", 5100, 1, 0, true)));
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());
        listener.start();

        passingCallbacks.get(0).accept(passing("1", 1_000_000L));

        verify(publisher, never()).publishEvent(any(LapPassingEvent.class));
        verify(publisher).publishEvent(new SplitPassingEvent(LapPassingEvent.NO_RACE, 7L, 1, "1", 1_000_000L));
    }

    @Test
    void aChange_retiresTheOldSourcesAndIgnoresTheirLatePassings() {
        when(repository.findAll()).thenReturn(List.of(new SplitDecoder(7L, "A", "h", 5100, 1, 0, true)));
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());
        listener.start();

        listener.onChanged(new SplitDecodersChangedEvent());
        passingCallbacks.get(0).accept(passing("1", 1_000_000L));

        verify(sources.get(0)).stop();
        assertThat(created).hasSize(2);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void stateOf_followsTheSourceAndDefaultsToDisconnected() {
        when(repository.findAll()).thenReturn(List.of(new SplitDecoder(7L, "A", "h", 5100, 1, 0, true)));
        listener.start();
        assertThat(listener.stateOf(7L)).isEqualTo("RECONNECTING");

        statusCallbacks.get(0).accept(AmbRc4TimingSource.ConnectionState.CONNECTED);

        assertThat(listener.stateOf(7L)).isEqualTo("CONNECTED");
        assertThat(listener.stateOf(99L)).isEqualTo("DISCONNECTED");
    }
}
