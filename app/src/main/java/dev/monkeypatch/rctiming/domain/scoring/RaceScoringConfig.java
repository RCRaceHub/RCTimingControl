package dev.monkeypatch.rctiming.domain.scoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * How an event's races are scored. {@link Classic} is today's behaviour, with each option set by its own field;
 * {@link Brca} is the BRCA 1/10 off-road rules, which also turn on the BRCA qualifying tie-break and flat DNS points.
 * Both carry the same fields, which are the Points Finals settings that decide scoring and not the finals layout
 * defaults: {@code bestHeatsCount} (rounds that count, 0 for all), {@code splitTiedPlaces}, {@code
 * maxRaceWindowSeconds} (0 for no limit), {@code finalLegs} (A final legs, 1 for one race) and {@code legsCounted}
 * (legs that count, 0 for all). {@code carsPerFinal} (0 splits the field evenly) and {@code fillTopFirst} are
 * defaults for the finals layout that the system offers a format, because choosing BRCA should still fill 10 cars and
 * fill A first.
 *
 * <p>A Points Finals format picks a system by id; its values are copied into the format when it is saved and into the
 * event class when it is added (see {@code RaceFormatService#resolveScoring}).
 */
public sealed interface RaceScoringConfig extends ScoringConfig {

    int bestHeatsCount();

    boolean splitTiedPlaces();

    int maxRaceWindowSeconds();

    int finalLegs();

    int legsCounted();

    int carsPerFinal();

    boolean fillTopFirst();

    @Override
    default ScoringKind kind() {
        return ScoringKind.RACE;
    }

    /** Whether the BRCA qualifying rules apply: flat DNS points and the countback tie-break. */
    @JsonIgnore
    boolean brcaQualifying();

    @Override
    default void validate() {
        range("bestHeatsCount", bestHeatsCount(), 0, 50);
        range("maxRaceWindowSeconds", maxRaceWindowSeconds(), 0, 24 * 3600);
        range("finalLegs", finalLegs(), 1, 10);
        range("legsCounted", legsCounted(), 0, finalLegs());
        range("carsPerFinal", carsPerFinal(), 0, 50);
    }

    private static void range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    /** Today's scoring, option by option. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Classic(int bestHeatsCount, boolean splitTiedPlaces, int maxRaceWindowSeconds, int finalLegs,
                   int legsCounted, int carsPerFinal, boolean fillTopFirst) implements RaceScoringConfig {
        @Override
        public boolean brcaQualifying() {
            return false;
        }
    }

    /** The BRCA 1/10 off-road rules. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Brca(int bestHeatsCount, boolean splitTiedPlaces, int maxRaceWindowSeconds, int finalLegs,
                int legsCounted, int carsPerFinal, boolean fillTopFirst) implements RaceScoringConfig {
        @Override
        public boolean brcaQualifying() {
            return true;
        }
    }
}
