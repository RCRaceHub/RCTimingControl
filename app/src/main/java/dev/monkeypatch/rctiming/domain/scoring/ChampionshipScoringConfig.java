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

    public ChampionshipScoringConfig {
        pointsScale = pointsScale == null ? List.of() : List.copyOf(pointsScale);
    }

    @Override
    public ScoringKind kind() {
        return ScoringKind.CHAMPIONSHIP;
    }

    @Override
    public void validate() {
        if ((bestX == null) != (bestY == null)) {
            throw new IllegalArgumentException("bestX and bestY must both be set or both be empty");
        }
        if (bestX != null && (bestX < 1 || bestY < bestX)) {
            throw new IllegalArgumentException("bestX must be at least 1 and no more than bestY");
        }
        if (tqBonusPoints < 0 || aFinalWinnerBonusPoints < 0) {
            throw new IllegalArgumentException("Bonus points cannot be negative");
        }
        Set<Integer> seen = new HashSet<>();
        for (ScalePoint p : pointsScale) {
            if (p.position() < 1 || p.points() < 0) {
                throw new IllegalArgumentException("Each scale entry needs a position of 1 or more and points of 0 or more");
            }
            if (!seen.add(p.position())) {
                throw new IllegalArgumentException("Position " + p.position() + " is in the points scale twice");
            }
        }
    }
}
