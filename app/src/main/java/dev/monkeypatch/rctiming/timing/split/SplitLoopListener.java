package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.decoderprotocol.timing.AmbRc4TimingSource;
import dev.monkeypatch.rctiming.decoderprotocol.timing.EpochCorrectedPassing;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.modules.ModuleSwitchedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Reads the split-loop decoders (#672) over RC-4 text, one {@link AmbRc4TimingSource} per enabled
 * {@link SplitDecoder}. The club's main decoder stays with {@code DecoderListener}; this class never credits a lap.
 *
 * <p>Each passing is shifted by the decoder's {@code clockOffsetMs}, handed to the single timing thread
 * ({@code timingExecutor}) and published there as a {@link SplitPassingEvent}, so the Netty thread never touches the
 * database. A connection state is kept per decoder for the admin page. Like {@code DecoderListener}, each source
 * carries a generation number so a retired source's late callbacks are ignored. Disable with
 * {@code app.decoder.listener.enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "app.decoder.listener", name = "enabled", matchIfMissing = true)
public class SplitLoopListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SplitLoopListener.class);

    /** Creates the TCP source. Replaceable in tests so callbacks can be driven directly. */
    @FunctionalInterface
    interface SourceFactory {
        AmbRc4TimingSource create(String host, int port, Consumer<EpochCorrectedPassing> onPassing,
                                  Consumer<AmbRc4TimingSource.ConnectionState> onStatus);
    }

    private final SplitDecoderRepository repository;
    private final RaceRepository raceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor timingExecutor;
    private final ModuleSwitch moduleSwitch;
    private final SourceFactory sourceFactory;

    // Guarded by this. The lifecycle, the change event and the Netty callbacks' status updates all take the lock.
    private final Map<Long, AmbRc4TimingSource> sources = new HashMap<>();
    private final Map<Long, String> states = new HashMap<>();
    private boolean running = false;
    // Written only while holding this lock. Read without it by the Netty callbacks.
    private volatile int generation = 0;

    @Autowired
    public SplitLoopListener(SplitDecoderRepository repository, RaceRepository raceRepository,
                             ApplicationEventPublisher eventPublisher,
                             @Qualifier("timingExecutor") Executor timingExecutor, ModuleSwitch moduleSwitch) {
        this(repository, raceRepository, eventPublisher, timingExecutor, moduleSwitch, AmbRc4TimingSource::new);
    }

    SplitLoopListener(SplitDecoderRepository repository, RaceRepository raceRepository,
                      ApplicationEventPublisher eventPublisher, Executor timingExecutor, ModuleSwitch moduleSwitch,
                      SourceFactory sourceFactory) {
        this.moduleSwitch = moduleSwitch;
        this.repository = repository;
        this.raceRepository = raceRepository;
        this.eventPublisher = eventPublisher;
        this.timingExecutor = timingExecutor;
        this.sourceFactory = sourceFactory;
    }

    @Override
    public synchronized void start() {
        running = true;
        reconnectAll(repository.findAll());
    }

    @Override
    public synchronized void stop() {
        running = false;
        stopSources();
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }

    /** Reconnects every split decoder after a change commits, so it never dials an address that was rolled back. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onChanged(SplitDecodersChangedEvent event) {
        if (running) {
            log.info("Split decoders changed - reconnecting");
            reconnectAll(repository.findAll());
        }
    }

    /** The connection state of one split decoder: {@code CONNECTED}, {@code RECONNECTING} or {@code DISCONNECTED}. */
    public synchronized String stateOf(long splitDecoderId) {
        return states.getOrDefault(splitDecoderId, "DISCONNECTED");
    }

    /** Starts or stops the connections when the split-timing module is switched on or off (#799). */
    @EventListener
    public synchronized void onModuleSwitched(ModuleSwitchedEvent event) {
        if (running && SplitTimingModule.ID.equals(event.moduleId())) {
            reconnectAll(repository.findAll());
        }
    }

    private void reconnectAll(List<SplitDecoder> decoders) {
        stopSources();
        if (!moduleSwitch.isEnabled(SplitTimingModule.ID)) {
            log.info("Split timing is switched off - no split decoder is read");
            return;
        }
        for (SplitDecoder decoder : decoders) {
            if (!decoder.enabled()) {
                continue;
            }
            log.info("Split decoder {} ({}) starting - {}:{}", decoder.splitNumber(), decoder.name(), decoder.host(),
                    decoder.port());
            int current = generation;
            AmbRc4TimingSource source = sourceFactory.create(decoder.host(), decoder.port(),
                    passing -> onPassing(current, decoder, passing), state -> onStatus(current, decoder.id(), state));
            sources.put(decoder.id(), source);
            states.put(decoder.id(), "RECONNECTING");
            source.start();
        }
    }

    /** Retires every source. Must be called while holding the lock. */
    private void stopSources() {
        generation++;
        sources.values().forEach(AmbRc4TimingSource::stop);
        sources.clear();
        states.clear();
    }

    /** Runs on a Netty thread. Ignored unless it comes from a current source. */
    void onStatus(int sourceGeneration, long splitDecoderId, AmbRc4TimingSource.ConnectionState state) {
        synchronized (this) {
            if (sourceGeneration == generation) {
                states.put(splitDecoderId, state.name());
            }
        }
    }

    /**
     * Hands one passing to the timing thread. The generation is checked again there, because the passing may wait
     * behind others while the source is retired. Package-private so tests can drive it without a socket.
     */
    void onPassing(int sourceGeneration, SplitDecoder decoder, EpochCorrectedPassing passing) {
        timingExecutor.execute(() -> {
            if (sourceGeneration == generation) {
                publish(decoder, passing);
            }
        });
    }

    /** Runs on the timing thread: finds the running race and publishes the split passing. */
    private void publish(SplitDecoder decoder, EpochCorrectedPassing passing) {
        try {
            long raceId = raceRepository.findFirstByStatus(RaceStatus.RUNNING)
                    .map(Race::getId)
                    .orElse(LapPassingEvent.NO_RACE);
            long micros = passing.rtcTimeMicros() + decoder.clockOffsetMs() * 1_000L;
            eventPublisher.publishEvent(new SplitPassingEvent(raceId, decoder.id(), decoder.splitNumber(),
                    passing.transponderNumber(), micros));
        } catch (RuntimeException e) {
            log.error("Could not handle split passing from transponder {}", passing.transponderNumber(), e);
        }
    }
}
