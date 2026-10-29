package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.decoderprotocol.timing.AmbRc4TimingSource;
import dev.monkeypatch.rctiming.decoderprotocol.timing.EpochCorrectedPassing;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.DecoderSettings;
import dev.monkeypatch.rctiming.domain.club.DecoderSettingsChangedEvent;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Reads the AMB decoder directly over TCP. This is the only source of live timing.
 *
 * <p>Connection settings come from the club profile ({@code decoder_host}, {@code decoder_port},
 * {@code decoder_protocol}). {@link AmbRc4TimingSource} owns the socket and reconnects with
 * backoff when it drops. When the settings change, the listener retires the current source and
 * starts a new one against the new address.
 *
 * <p>Each passing is published as a {@link LapPassingEvent}, using the active-race lookup
 * ({@link LapPassingEvent#NO_RACE} when no race is running). {@link LapTimingService} and the practice
 * timing service consume it. The Netty thread only hands the passing to the single timing thread
 * ({@code timingExecutor}); the race lookup, the publish and every listener run there, in decoder order.
 * Disable with {@code app.decoder.listener.enabled=false}.
 *
 * <p>Only the RC-4 text protocol is supported. Any other protocol setting leaves the listener
 * idle and reports the decoder as disconnected.
 *
 * <p><strong>Retired sources:</strong> {@link AmbRc4TimingSource#stop()} is asynchronous, so a
 * connection attempt in flight can still call back after the source has been replaced. Each
 * source is tagged with a generation number. Retiring a source bumps the generation first, so
 * late passings and status changes from it are ignored.
 */
@Component
@ConditionalOnProperty(prefix = "app.decoder.listener", name = "enabled", matchIfMissing = true)
public class DecoderListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DecoderListener.class);

    /** Creates the TCP source. Replaceable in tests so callbacks can be driven directly. */
    @FunctionalInterface
    interface SourceFactory {
        AmbRc4TimingSource create(String host, int port,
                                  Consumer<EpochCorrectedPassing> onPassing,
                                  Consumer<AmbRc4TimingSource.ConnectionState> onStatus);
    }

    private final ClubProfileService clubProfileService;
    private final RaceRepository raceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DecoderStatusPublisher statusPublisher;
    private final SourceFactory sourceFactory;
    private final Executor timingExecutor;

    // Guarded by this. Settings changes and lifecycle calls both take the lock.
    private AmbRc4TimingSource source;
    private DecoderSettings currentSettings;
    private boolean running = false;
    // Written only while holding this lock. Read without it by the Netty callbacks.
    private volatile int generation = 0;

    @Autowired
    public DecoderListener(ClubProfileService clubProfileService,
                           RaceRepository raceRepository,
                           ApplicationEventPublisher eventPublisher,
                           DecoderStatusPublisher statusPublisher,
                           @Qualifier("timingExecutor") Executor timingExecutor) {
        this(clubProfileService, raceRepository, eventPublisher, statusPublisher, timingExecutor,
                AmbRc4TimingSource::new);
    }

    DecoderListener(ClubProfileService clubProfileService,
                    RaceRepository raceRepository,
                    ApplicationEventPublisher eventPublisher,
                    DecoderStatusPublisher statusPublisher,
                    Executor timingExecutor,
                    SourceFactory sourceFactory) {
        this.clubProfileService = clubProfileService;
        this.raceRepository = raceRepository;
        this.eventPublisher = eventPublisher;
        this.statusPublisher = statusPublisher;
        this.timingExecutor = timingExecutor;
        this.sourceFactory = sourceFactory;
    }

    @Override
    public synchronized void start() {
        running = true;
        applySettings(clubProfileService.getDecoderSettings());
    }

    /**
     * {@link AmbRc4TimingSource#stop()} is fire-and-forget, so SmartLifecycle's default {@code stop(Runnable)}, which
     * calls this and then the callback, is all Spring needs.
     */
    @Override
    public synchronized void stop() {
        running = false;
        stopSource();
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * Applies new decoder settings after the club profile update commits. Runs after commit so
     * the listener never reconnects to an address that was rolled back.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onSettingsChanged(DecoderSettingsChangedEvent event) {
        if (!running) {
            return;
        }
        log.info("Decoder settings changed — restarting direct decoder listener");
        applySettings(event.settings());
    }

    /**
     * Catches settings written without an event, such as a database seed or a manual SQL change.
     * Reconnects if the stored settings differ from the ones the current source was built for.
     */
    @Scheduled(initialDelay = 15_000, fixedDelay = 15_000)
    public synchronized void reconcile() {
        if (!running) {
            return;
        }
        DecoderSettings latest = clubProfileService.getDecoderSettings();
        if (!latest.equals(currentSettings)) {
            log.info("Stored decoder settings differ from the active listener — reconnecting");
            applySettings(latest);
        }
    }

    private void applySettings(DecoderSettings settings) {
        stopSource();
        currentSettings = settings;

        if (!settings.isConfigured()) {
            log.info("Decoder not configured — direct decoder listener idle");
            statusPublisher.onDecoderStatus(AmbRc4TimingSource.ConnectionState.DISCONNECTED.name());
            return;
        }
        if (!"RC4".equals(settings.protocol())) {
            log.warn("Decoder protocol {} is not supported by the direct listener yet (RC4 only) — listener idle",
                     settings.protocol());
            statusPublisher.onDecoderStatus(AmbRc4TimingSource.ConnectionState.DISCONNECTED.name());
            return;
        }

        log.info("Direct decoder listener starting — decoder={}:{}", settings.host(), settings.port());
        int current = generation;
        source = sourceFactory.create(settings.host(), settings.port(),
                passing -> onPassing(current, passing),
                state -> onStatus(current, state));
        source.start();
    }

    /** Retires the current source. Must be called while holding the lock. */
    private void stopSource() {
        // Bump the generation before stopping, so the retiring source's own DISCONNECTED
        // callback (and anything still in flight from it) is ignored.
        generation++;
        if (source != null) {
            source.stop();
            source = null;
        }
    }

    /** Runs on the Netty event-loop thread. Ignored unless it comes from the current source. */
    void onStatus(int sourceGeneration, AmbRc4TimingSource.ConnectionState state) {
        synchronized (this) {
            if (sourceGeneration != generation) {
                return;
            }
            statusPublisher.onDecoderStatus(state.name());
        }
    }

    /** As {@link #onPassing(int, EpochCorrectedPassing)}, for the source that is current right now. */
    void onPassing(EpochCorrectedPassing passing) {
        onPassing(generation, passing);
    }

    /**
     * Hands one decoded passing to the timing thread. Runs on the Netty event-loop thread, which must
     * not touch the database, so the race lookup happens in {@link #publishPassing}. The generation is
     * checked again when the timing thread gets to it, not only here: the passing may wait behind others,
     * and the source can be retired in the meantime. Package-private so it can be exercised without a
     * socket.
     */
    void onPassing(int sourceGeneration, EpochCorrectedPassing passing) {
        timingExecutor.execute(() -> {
            if (sourceGeneration == generation) {
                publishPassing(passing);
            }
        });
    }

    /** Runs on the timing thread: finds the running race and publishes the passing to its listeners. */
    private void publishPassing(EpochCorrectedPassing passing) {
        try {
            long raceId = raceRepository.findFirstByStatus(RaceStatus.RUNNING)
                    .map(Race::getId)
                    .orElse(LapPassingEvent.NO_RACE);
            eventPublisher.publishEvent(
                    new LapPassingEvent(raceId, passing.transponderNumber(), passing.rtcTimeMicros()));
        } catch (RuntimeException e) {
            // One bad passing must not stop the ones behind it
            log.error("Could not handle passing from transponder {}", passing.transponderNumber(), e);
        }
    }
}
