package dev.monkeypatch.rctiming.domain.club;

import java.util.List;

/**
 * The club's live timing thresholds (#505): when two cars are in an on-track battle and which way it is going, how
 * near the car ahead counts as close, and when a lap bar turns red. Held on the {@link ClubProfile}; RCTC runs for one
 * club, so there is one set. Every time is in milliseconds.
 *
 * @param battleGapMs         the largest gap between adjacent cars that makes a battle; 0 switches battles off
 * @param battleTrendMsPerLap a gap changing by more than this each lap is closing or growing rather than holding
 * @param battleFastMsPerLap  a gap closing by this much or more each lap marks a car gaining fast
 * @param battleHoldLaps      laps a pair stays a battle after it drops outside the gap
 * @param closeGapMs          how near the car ahead is shown as close (the amber row); 0 switches the tint off
 * @param lateMarginMs        how far past its average lap a car is before its lap bar turns red
 * @param hiddenRowIcons      the row icons the referee screen, the live race board and the board views leave out; each is
 *                            one of {@link #ROW_ICONS}. The streaming overlay chooses its own in its theme.
 */
public record LiveTimingSettings(int battleGapMs, int battleTrendMsPerLap, int battleFastMsPerLap, int battleHoldLaps,
                                 int closeGapMs, int lateMarginMs, List<String> hiddenRowIcons) {

    /** The icons that can be switched off beside a driver's name: pace thumbs, flying-lap flame, lap flash, close, lapped, penalty and no-signal. */
    public static final List<String> ROW_ICONS = List.of("pace", "flying", "lap", "close", "lapped", "penalty", "signal");

    public LiveTimingSettings {
        // Nulls and repeats are dropped, so a sloppy request body cannot fail the copy or be stored twice
        hiddenRowIcons = hiddenRowIcons == null ? List.of()
                : hiddenRowIcons.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** The settings until the club changes them. */
    public static LiveTimingSettings defaults() {
        return new LiveTimingSettings(2000, 150, 300, 2, 1000, 1000, List.of());
    }

    /**
     * Checks every value is in range, so a typo cannot switch a screen off by accident or hide every battle.
     *
     * @throws IllegalArgumentException naming the first value out of range
     */
    public LiveTimingSettings validated() {
        require("The battle gap", battleGapMs, 0, 10_000);
        require("The battle trend limit", battleTrendMsPerLap, 0, 2_000);
        require("The gaining-fast limit", battleFastMsPerLap, 0, 5_000);
        if (battleFastMsPerLap < battleTrendMsPerLap) {
            throw new IllegalArgumentException("The gaining-fast limit cannot be below the battle trend limit");
        }
        require("The battle hold", battleHoldLaps, 0, 10);
        require("The close gap", closeGapMs, 0, 60_000);
        require("The late margin", lateMarginMs, 0, 10_000);
        for (String icon : hiddenRowIcons) {
            if (!ROW_ICONS.contains(icon)) {
                throw new IllegalArgumentException("Unknown row icon " + icon);
            }
        }
        return this;
    }

    private static void require(String what, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be from " + min + " to " + max);
        }
    }
}
