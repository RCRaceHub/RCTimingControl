package dev.monkeypatch.rctiming.domain.event;

import java.util.List;

/**
 * How practice is ranked on the live top board: each driver's fastest lap, or the quickest run of 2 or 3 laps in a
 * row added together. Chosen per event ({@link Event#getPracticeRanking()}), because clubs differ on which counts
 * and the two cannot be compared across events.
 */
public enum PracticeRanking {
    FASTEST("Fastest lap", 1),
    BEST_2("Best 2 laps in a row", 2),
    BEST_3("Best 3 laps in a row", 3);

    private final String label;
    private final int laps;

    PracticeRanking(String label, int laps) {
        this.label = label;
        this.laps = laps;
    }

    /** The heading a board gives the ranked time, such as "Best 3 laps in a row". */
    public String label() {
        return label;
    }

    /**
     * The driver's ranked time in milliseconds over one practice heat: the fastest lap, or the smallest sum of
     * {@link #laps} consecutive laps. A lap with no time breaks a run. Null when the driver has no qualifying run,
     * such as 2 laps when the ranking wants 3.
     *
     * @param lapTimesMs the lap times in the order they were driven; null entries are laps with no known time
     */
    public Long best(List<Long> lapTimesMs) {
        Long best = null;
        for (int start = 0; start + laps <= lapTimesMs.size(); start++) {
            long sum = 0;
            boolean complete = true;
            for (int i = start; i < start + laps; i++) {
                Long lap = lapTimesMs.get(i);
                if (lap == null) {
                    complete = false;
                    break;
                }
                sum += lap;
            }
            if (complete && (best == null || sum < best)) {
                best = sum;
            }
        }
        return best;
    }
}
