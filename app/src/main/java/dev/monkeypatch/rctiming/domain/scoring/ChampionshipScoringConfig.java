package dev.monkeypatch.rctiming.domain.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How a championship turns places into points ({@code PLACE_POINTS}): the points for each finishing place, the best X
 * rounds that count (both null for all of them), the TQ and A final winner bonuses and whether the standings also have
 * an overall table across classes. These are the fields a championship holds itself; a later change lets an official
 * pick a system to fill them in. The highest total wins and ties go to countback, as in {@code docs/scoring.md}.
 *
 * @param bestX rounds that count, null with {@code bestY} for all of them
 * @param bestY rounds the season has, shown only
 * @param pointsScale points per place; a place not listed scores 0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChampionshipScoringConfig(Integer bestX, Integer bestY, int tqBonusPoints, int aFinalWinnerBonusPoints,
                                        boolean showOverallTable, List<ScalePoint> pointsScale)
        implements ScoringConfig {

    /** Points for one finishing place. */
    public record ScalePoint(int position, int points) {}

    /** Upper bounds, so a file cannot carry values that overflow a total later. */
    private static final int MAX_ROUNDS = 100;
    private static final int MAX_SCALE_ENTRIES = 200;
    private static final int MAX_POINTS = 10_000;

    public ChampionshipScoringConfig {
        pointsScale = pointsScale == null ? List.of() : List.copyOf(pointsScale);
    }

    @Override
    public ScoringKind kind() {
        return ScoringKind.CHAMPIONSHIP;
    }

    @Override
    public void validate() {
        if (bestX != null && bestY != null && bestY > MAX_ROUNDS) {
            throw new IllegalArgumentException("bestY cannot be more than " + MAX_ROUNDS);
        }
        if (pointsScale.size() > MAX_SCALE_ENTRIES) {
            throw new IllegalArgumentException("The points scale cannot have more than " + MAX_SCALE_ENTRIES + " places");
        }
        if ((bestX == null) != (bestY == null)) {
            throw new IllegalArgumentException("bestX and bestY must both be set or both be empty");
        }
        if (bestX != null && (bestX < 1 || bestY < bestX)) {
            throw new IllegalArgumentException("bestX must be at least 1 and no more than bestY");
        }
        if (tqBonusPoints < 0 || aFinalWinnerBonusPoints < 0 || tqBonusPoints > MAX_POINTS
                || aFinalWinnerBonusPoints > MAX_POINTS) {
            throw new IllegalArgumentException("Bonus points must be between 0 and " + MAX_POINTS);
        }
        Set<Integer> seen = new HashSet<>();
        for (ScalePoint p : pointsScale) {
            if (p.position() < 1 || p.position() > MAX_SCALE_ENTRIES || p.points() < 0 || p.points() > MAX_POINTS) {
                throw new IllegalArgumentException("Each scale entry needs a position of 1 to " + MAX_SCALE_ENTRIES
                        + " and points of 0 to " + MAX_POINTS);
            }
            if (!seen.add(p.position())) {
                throw new IllegalArgumentException("Position " + p.position() + " is in the points scale twice");
            }
        }
    }
}
