package dev.monkeypatch.rctiming.simulator;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerativeModeTest {

    @Test
    void lapTimesVaryInSmallIncrementsAndStayNearTheBaseInterval() {
        Random rng = new Random(42);
        Set<Long> distinctMs = new TreeSet<>();
        for (int i = 0; i < 500; i++) {
            double lap = GenerativeMode.nextLapMs(12_500, 1.0, 2_500, rng);
            distinctMs.add(Math.round(lap));
            assertTrue(lap >= 6_250 && lap <= 12_500 + 2_500 * 2.5 + 2_500, "lap out of range: " + lap);
        }
        assertTrue(distinctMs.size() > 300, "expected plenty of distinct millisecond values, got " + distinctMs.size());
    }

    @Test
    void driversGetDifferentFixedPaces() {
        double[] pace = GenerativeMode.paceFactors(6, new Random(7));
        assertEquals(6, pace.length);
        assertEquals(6, Arrays.stream(pace).distinct().count());
        for (double p : pace) {
            assertTrue(p >= 0.95 && p <= 1.05);
        }
    }

    @Test
    void zeroJitterGivesSteadyLapsAtTheDriversPace() {
        assertEquals(12_500 * 0.98, GenerativeMode.nextLapMs(12_500, 0.98, 0, new Random(1)), 1e-9);
    }
}
