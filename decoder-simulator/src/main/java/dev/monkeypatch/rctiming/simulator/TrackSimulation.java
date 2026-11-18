package dev.monkeypatch.rctiming.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A pretend track shared by several fake decoders (#678): a start/finish loop and any number of split loops, with the
 * same cars crossing all of them. Where {@link GenerativeMode} gives each decoder its own unrelated laps, this one
 * decides each car's lap times once, so a split loop sees a car a fixed share of the way round each lap after the
 * finish loop saw it.
 *
 * <p>Times are epoch milliseconds, as fractions. Lap times come from {@link GenerativeMode#nextLapMs}, so they vary
 * the way the generative mode's do. The simulation is deterministic for a given seed. It holds no threads: every
 * decoder session asks for the crossings it must send and the simulation extends each car's laps as far as needed, so
 * calls are synchronised.
 */
final class TrackSimulation {

    /** One car crossing one loop. {@code loop} is 0 for the start/finish loop, 1 for the first split, and so on. */
    record Crossing(int loop, String transponder, double timeMs) {
    }

    /** The finish-loop crossing times of one car, oldest first, grown on demand. */
    private static final class Car {
        final String transponder;
        final double pace;
        final Random rng;
        final List<Double> finishTimesMs = new ArrayList<>();

        Car(String transponder, double pace, Random rng) {
            this.transponder = transponder;
            this.pace = pace;
            this.rng = rng;
        }
    }

    private final List<Car> cars = new ArrayList<>();
    private final double[] splitPositions;
    private final long intervalMs;
    private final long jitterMs;

    /**
     * @param transponders   the cars
     * @param intervalMs     base lap time, ms
     * @param jitterMs       lap-time noise, as for {@link GenerativeMode}
     * @param splitPositions where each split loop is, as a share of the lap after the finish loop: 0.35 is just over a
     *                       third of the way round; each must be above 0 and below 1, and later splits further round
     * @param startMs        when the race starts, epoch ms; each car's first finish crossing is at the start plus
     *                       its share of one lap, so the cars are staggered across a lap
     * @param seed           seed for lap times
     */
    TrackSimulation(List<String> transponders, long intervalMs, long jitterMs, double[] splitPositions,
                    double startMs, long seed) {
        this.intervalMs = intervalMs;
        this.jitterMs = jitterMs;
        this.splitPositions = splitPositions.clone();
        Random rng = new Random(seed);
        double[] pace = GenerativeMode.paceFactors(Math.max(1, transponders.size()), rng);
        for (int i = 0; i < transponders.size(); i++) {
            Car car = new Car(transponders.get(i), pace[i], new Random(rng.nextLong()));
            double stagger = i * intervalMs / (double) Math.max(1, transponders.size());
            car.finishTimesMs.add(startMs + stagger);
            cars.add(car);
        }
    }

    /** How many loops there are: the finish loop and every split. */
    int loopCount() {
        return 1 + splitPositions.length;
    }

    /**
     * Every crossing of one loop with a time in {@code [fromMs, toMs)}, oldest first.
     *
     * @param loop 0 for the start/finish loop, 1 for the first split, and so on
     */
    synchronized List<Crossing> crossings(int loop, double fromMs, double toMs) {
        List<Crossing> found = new ArrayList<>();
        for (Car car : cars) {
            // A lap is only fully known once the next finish crossing is, so the car's laps run one past toMs
            while (car.finishTimesMs.get(car.finishTimesMs.size() - 1) <= toMs + 2.0 * intervalMs) {
                double last = car.finishTimesMs.get(car.finishTimesMs.size() - 1);
                car.finishTimesMs.add(last + GenerativeMode.nextLapMs(intervalMs, car.pace, jitterMs, car.rng));
            }
            for (int lap = 0; lap < car.finishTimesMs.size() - 1; lap++) {
                double start = car.finishTimesMs.get(lap);
                double time = loop == 0
                        ? start
                        : start + splitPositions[loop - 1] * (car.finishTimesMs.get(lap + 1) - start);
                if (time >= fromMs && time < toMs) {
                    found.add(new Crossing(loop, car.transponder, time));
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.timeMs(), b.timeMs()));
        return found;
    }
}
