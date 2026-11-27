package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.decoderprotocol.timing.AmbRc4TimingSource;
import dev.monkeypatch.rctiming.decoderprotocol.timing.ClockHealth;
import dev.monkeypatch.rctiming.decoderprotocol.timing.EpochCorrectedPassing;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.DecoderSettings;
import dev.monkeypatch.rctiming.domain.club.DecoderSettingsChangedEvent;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DecoderListener} that never open a socket. The source factory is
 * replaced with one that records each source's callbacks, so tests can drive them directly.
 * The socket path is covered by {@code DecoderListenerIT}.
 */
class DecoderListenerTest {

    private ClubProfileService clubProfileService;
    private RaceRepository raceRepository;
    private ApplicationEventPublisher eventPublisher;
    private DecoderStatusPublisher statusPublisher;
    private DecoderListener listener;
    /** Runs on the calling thread by default; the hand-off test swaps in one that queues the work. */
    private Executor timingExecutor = Runnable::run;

    /** Callbacks for each source the listener creates, in creation order. */
    private final List<Consumer<EpochCorrectedPassing>> passingCallbacks = new ArrayList<>();
    private final List<Consumer<AmbRc4TimingSource.ConnectionState>> statusCallbacks = new ArrayList<>();
    private final List<AmbRc4TimingSource> sources = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clubProfileService = mock(ClubProfileService.class);
        raceRepository = mock(RaceRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        statusPublisher = mock(DecoderStatusPublisher.class);
        when(clubProfileService.getDecoderSettings()).thenReturn(new DecoderSettings(null, null, null));
        listener = newListener();
    }

    private DecoderListener newListener() {
        return new DecoderListener(clubProfileService, raceRepository, eventPublisher, statusPublisher,
                command -> timingExecutor.execute(command),
                (host, port, onPassing, onStatus) -> {
                    passingCallbacks.add(onPassing);
                    statusCallbacks.add(onStatus);
                    AmbRc4TimingSource source = mock(AmbRc4TimingSource.class);
                    sources.add(source);
                    return source;
                });
    }

    @Test
    void onPassing_runningRace_publishesEventWithRaceId() {
        Race race = mock(Race.class);
        when(race.getId()).thenReturn(77L);
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.of(race));

        listener.onPassing(new EpochCorrectedPassing("1234567", 1_000_000L, 1, 1, 63, 1));

        ArgumentCaptor<LapPassingEvent> captor = ArgumentCaptor.forClass(LapPassingEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LapPassingEvent(77L, "1234567", 1_000_000L));
    }

    @Test
    void onPassing_noRunningRace_publishesEventWithSentinelRaceIdZero() {
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());

        assertThatCode(() -> listener.onPassing(new EpochCorrectedPassing("7654321", 2_000_000L, 1, 1, 63, 1)))
                .doesNotThrowAnyException();

        ArgumentCaptor<LapPassingEvent> captor = ArgumentCaptor.forClass(LapPassingEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().raceId()).isZero();
    }

    @Test
    void onPassing_doesNoWorkOnTheCallingThread_untilTheTimingThreadRunsIt() {
        List<Runnable> queued = new ArrayList<>();
        timingExecutor = queued::add;
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());

        listener.onPassing(new EpochCorrectedPassing("1234567", 1_000_000L, 1, 1, 63, 1));

        // The Netty thread has only queued the passing: no database lookup, nothing published
        assertThat(queued).hasSize(1);
        verify(raceRepository, never()).findFirstByStatus(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        queued.get(0).run();

        verify(eventPublisher).publishEvent(new LapPassingEvent(LapPassingEvent.NO_RACE, "1234567", 1_000_000L));
    }

    @Test
    void onPassing_aFailedLookupDoesNotStopTheNextPassing() {
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING))
                .thenThrow(new IllegalStateException("database busy"))
                .thenReturn(Optional.empty());

        listener.onPassing(new EpochCorrectedPassing("1", 1_000_000L, 1, 1, 63, 1));
        listener.onPassing(new EpochCorrectedPassing("2", 2_000_000L, 1, 1, 63, 1));

        verify(eventPublisher).publishEvent(new LapPassingEvent(LapPassingEvent.NO_RACE, "2", 2_000_000L));
    }

    @Test
    void aPassingQueuedBeforeTheSourceWasRetired_isNotPublishedWhenItsTurnComes() {
        List<Runnable> queued = new ArrayList<>();
        timingExecutor = queued::add;
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());
        listener.start();
        configure("localhost", 5100, "RC4");

        passingCallbacks.get(0).accept(new EpochCorrectedPassing("1234567", 1_000_000L, 1, 1, 63, 1));
        assertThat(queued).hasSize(1);
        configure("localhost", 5200, "RC4");   // retires the first source while its passing waits
        queued.get(0).run();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(raceRepository, never()).findFirstByStatus(any());
    }

    @Test
    void currentSourceStatus_isPublishedToStatusPublisher() {
        listener.start();
        configure("localhost", 5100, "RC4");

        statusCallbacks.get(0).accept(AmbRc4TimingSource.ConnectionState.RECONNECTING);

        verify(statusPublisher).onDecoderStatus("RECONNECTING");
    }

    @SuppressWarnings("unchecked")
    private Consumer<ClockHealth> clockListenerOf(AmbRc4TimingSource source) {
        ArgumentCaptor<Consumer<ClockHealth>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(source).setClockHealthListener(captor.capture());
        return captor.getValue();
    }

    @Test
    void currentSourceClockHealth_isPublishedToStatusPublisher() {
        listener.start();
        configure("localhost", 5100, "RC4");
        ClockHealth drifting = new ClockHealth(ClockHealth.Status.DRIFTING, 40, 12, 3, 9, 0);

        clockListenerOf(sources.get(0)).accept(drifting);

        verify(statusPublisher).onClockHealth(drifting);
    }

    @Test
    void clockHealthFromARetiredSource_isIgnored() {
        listener.start();
        configure("localhost", 5100, "RC4");
        Consumer<ClockHealth> old = clockListenerOf(sources.get(0));
        configure("localhost", 5101, "RC4");
        ClockHealth drifting = new ClockHealth(ClockHealth.Status.DRIFTING, 40, 12, 3, 9, 0);

        old.accept(drifting);

        verify(statusPublisher, never()).onClockHealth(drifting);
    }

    @Test
    void currentSourcePassing_isPublishedAsLapPassingEvent() {
        when(raceRepository.findFirstByStatus(RaceStatus.RUNNING)).thenReturn(Optional.empty());
        listener.start();
        configure("localhost", 5100, "RC4");

        passingCallbacks.get(0).accept(new EpochCorrectedPassing("1234567", 3_000_000L, 1, 1, 63, 1));

        verify(eventPublisher).publishEvent(new LapPassingEvent(0L, "1234567", 3_000_000L));
    }

    @Test
    void start_unconfiguredDecoder_reportsDisconnectedAndStaysIdle() {
        when(clubProfileService.getDecoderSettings()).thenReturn(new DecoderSettings(null, null, null));

        listener.start();

        verify(statusPublisher).onDecoderStatus("DISCONNECTED");
        assertThat(listener.isRunning()).isTrue();
        assertThat(statusCallbacks).isEmpty();
    }

    @Test
    void start_p3Protocol_isNotYetSupported_reportsDisconnectedAndStaysIdle() {
        when(clubProfileService.getDecoderSettings()).thenReturn(new DecoderSettings("192.168.1.10", 5403, "P3"));

        listener.start();

        verify(statusPublisher).onDecoderStatus("DISCONNECTED");
        assertThat(statusCallbacks).isEmpty();
    }

    @Test
    void settingsChanged_whenNotRunning_doesNothing() {
        listener.onSettingsChanged(new DecoderSettingsChangedEvent(new DecoderSettings("localhost", 5100, "RC4")));

        verify(statusPublisher, never()).onDecoderStatus(any());
        assertThat(statusCallbacks).isEmpty();
    }

    @Test
    void lateStatusFromRetiredSource_afterAddressChange_isIgnored() {
        listener.start();
        configure("localhost", 5100, "RC4");
        configure("localhost", 5200, "RC4");

        // The first source (index 0) was still in flight when it was replaced by index 1.
        statusCallbacks.get(0).accept(AmbRc4TimingSource.ConnectionState.CONNECTED);
        statusCallbacks.get(0).accept(AmbRc4TimingSource.ConnectionState.RECONNECTING);
        verify(statusPublisher, never()).onDecoderStatus("CONNECTED");
        verify(statusPublisher, never()).onDecoderStatus("RECONNECTING");

        // The replacement's status is still applied.
        statusCallbacks.get(1).accept(AmbRc4TimingSource.ConnectionState.CONNECTED);
        verify(statusPublisher).onDecoderStatus("CONNECTED");
    }

    @Test
    void lateReconnectingFromRetiredSource_afterSettingsCleared_doesNotOverwriteDisconnected() {
        listener.start();
        configure("localhost", 5100, "RC4");

        configure(null, null, null);
        statusCallbacks.get(0).accept(AmbRc4TimingSource.ConnectionState.RECONNECTING);

        ArgumentCaptor<String> states = ArgumentCaptor.forClass(String.class);
        verify(statusPublisher, atLeastOnce()).onDecoderStatus(states.capture());
        assertThat(states.getAllValues()).doesNotContain("RECONNECTING");
        assertThat(states.getValue()).isEqualTo("DISCONNECTED");
    }

    @Test
    void latePassingFromRetiredSource_isNotPublished() {
        listener.start();
        configure("localhost", 5100, "RC4");
        configure("localhost", 5200, "RC4");

        passingCallbacks.get(0).accept(new EpochCorrectedPassing("1234567", 1_000_000L, 1, 1, 63, 1));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reconcile_storedSettingsChangedWithoutEvent_reconnectsToThem() {
        listener.start();
        assertThat(statusCallbacks).isEmpty();

        // Settings written directly to the database (for example by the demo data), with no event.
        when(clubProfileService.getDecoderSettings()).thenReturn(new DecoderSettings("fake-decoder", 5100, "RC4"));
        listener.reconcile();

        assertThat(statusCallbacks).hasSize(1);
    }

    @Test
    void reconcile_settingsUnchanged_doesNotRebuildSource() {
        when(clubProfileService.getDecoderSettings()).thenReturn(new DecoderSettings("localhost", 5100, "RC4"));
        listener.start();
        assertThat(statusCallbacks).hasSize(1);

        listener.reconcile();

        assertThat(statusCallbacks).hasSize(1);
    }

    @Test
    void stopWithCallback_invokesCallback_evenWhenNeverStarted() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        listener.stop(() -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(listener.isRunning()).isFalse();
    }

    /** Simulates the club profile being saved, as {@code ClubProfileService} does after commit. */
    private void configure(String host, Integer port, String protocol) {
        listener.onSettingsChanged(new DecoderSettingsChangedEvent(new DecoderSettings(host, port, protocol)));
    }
}
