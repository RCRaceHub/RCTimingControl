package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Works out sector times from the finish loop's passings and the split loops' passings (#672).
 *
 * <p>Both kinds arrive as events published on the single timing thread, so this class needs no locking: it only ever
 * runs there; the one exception is the change notice, which only bumps a volatile counter. For each transponder it remembers when the car last crossed the finish loop and which splits it has hit
 * since. When the car crosses the finish loop again and has hit every enabled split in order, it publishes a
 * {@link SectorTimesEvent}. A lap with a missing or out-of-order split gets no sector times at all, never wrong ones.
 *
 * <p>Nothing here changes how laps are credited: {@code LapTimingService} still reads only {@link LapPassingEvent}.
 * State is in memory and per race: a passing for another race, or for no race, starts afresh. A finish crossing within
 * {@value #MIN_FINISH_GAP_SECONDS} seconds of the last one is treated as a double read and ignored.
 */
@Service
public class SectorTimingService {

    /** Two finish reads closer than this are one crossing seen twice. */
    static final long MIN_FINISH_GAP_SECONDS = 3;
    private static final long MIN_FINISH_GAP_MICROS = MIN_FINISH_GAP_SECONDS * 1_000_000L;

    /** One car's progress round the lap it is on. */
    private static final class CarLap {
        long finishMicros;
        int lapNumber;
        /** Split number to crossing time, in split order. */
        final TreeMap<Integer, Long> splits = new TreeMap<>();
    }

    private final SplitDecoderRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, CarLap> cars = new HashMap<>();
    private long currentRaceId = LapPassingEvent.NO_RACE;
    private List<Integer> expectedSplits = null;
    /** Bumped by a request thread when the split decoders change; the timing thread resets its state when it sees it. */
    private volatile int configVersion = 0;
    private int seenConfigVersion = 0;

    public SectorTimingService(SplitDecoderRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Notes that the split decoders changed. Runs on the request thread after the change commits, so it only bumps a
     * counter; the timing thread then drops its cached splits and every car's progress, which was measured against
     * the old set of splits, the next time it handles a passing.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecodersChanged(SplitDecodersChangedEvent event) {
        configVersion++;
    }

    /** A finish-loop crossing: closes the car's lap if it has every split, and starts the next one. */
    @EventListener
    public void onFinishCrossing(LapPassingEvent event) {
        if (event.raceId() == LapPassingEvent.NO_RACE || splitsExpected().isEmpty()) {
            return;
        }
        resetIfNewRace(event.raceId());
        CarLap car = cars.get(event.transponderNumber());
        if (car == null) {
            car = new CarLap();
            car.finishMicros = event.rtcTimeMicros();
            cars.put(event.transponderNumber(), car);
            return;
        }
        if (event.rtcTimeMicros() - car.finishMicros < MIN_FINISH_GAP_MICROS) {
            return;
        }
        car.lapNumber++;
        List<Long> sectors = sectorsOf(car, event.rtcTimeMicros());
        if (sectors != null) {
            eventPublisher.publishEvent(
                    new SectorTimesEvent(event.raceId(), event.transponderNumber(), car.lapNumber, sectors));
        }
        car.finishMicros = event.rtcTimeMicros();
        car.splits.clear();
    }

    /** A split-loop crossing: noted against the car's current lap. Ignored before the car's first finish crossing. */
    @EventListener
    public void onSplitCrossing(SplitPassingEvent event) {
        if (event.raceId() == LapPassingEvent.NO_RACE) {
            return;
        }
        splitsExpected();
        resetIfNewRace(event.raceId());
        CarLap car = cars.get(event.transponderNumber());
        if (car == null || event.rtcTimeMicros() <= car.finishMicros) {
            return;
        }
        // A second read of the same split keeps the first
        car.splits.putIfAbsent(event.splitNumber(), event.rtcTimeMicros());
    }

    /**
     * The car's sector times for the lap that ended at {@code finishMicros}, or null when a split is missing or the
     * splits were not crossed in order.
     */
    private List<Long> sectorsOf(CarLap car, long finishMicros) {
        if (!new ArrayList<>(car.splits.keySet()).equals(splitsExpected())) {
            return null;
        }
        List<Long> sectors = new ArrayList<>();
        long previous = car.finishMicros;
        for (long crossing : car.splits.values()) {
            if (crossing <= previous) {
                return null;
            }
            sectors.add(crossing - previous);
            previous = crossing;
        }
        if (finishMicros <= previous) {
            return null;
        }
        sectors.add(finishMicros - previous);
        return sectors;
    }

    private List<Integer> splitsExpected() {
        int version = configVersion;
        if (version != seenConfigVersion) {
            seenConfigVersion = version;
            expectedSplits = null;
            cars.clear();
        }
        if (expectedSplits == null) {
            expectedSplits = repository.findAll().stream().filter(SplitDecoder::enabled)
                    .map(SplitDecoder::splitNumber).sorted().toList();
        }
        return expectedSplits;
    }

    private void resetIfNewRace(long raceId) {
        if (raceId != currentRaceId) {
            cars.clear();
            currentRaceId = raceId;
        }
    }
}
