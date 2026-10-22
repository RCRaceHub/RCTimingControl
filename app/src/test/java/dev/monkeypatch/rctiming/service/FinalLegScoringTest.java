package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.service.FinalLegScoring.Run;
import dev.monkeypatch.rctiming.service.FinalLegScoring.Standing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinalLegScoringTest {

    private static Run run(int laps, long timeMs) {
        return new Run(laps, timeMs);
    }

    @Test
    void theBestTwoOfThreeLegsCountAndTheWorstIsDropped() {
        // Driver 1 wins legs 1 and 2 and crashes in 3; driver 2 is second, second, first
        List<Map<Long, Run>> legs = List.of(
                Map.of(1L, run(13, 300_000), 2L, run(12, 300_000), 3L, run(10, 300_000)),
                Map.of(1L, run(13, 301_000), 2L, run(12, 300_000), 3L, run(11, 300_000)),
                Map.of(1L, run(5, 300_000), 2L, run(13, 300_000), 3L, run(12, 300_000)));

        List<Standing> ranked = FinalLegScoring.rank(legs, List.of(1L, 2L, 3L), 2);

        assertThat(ranked).extracting(Standing::entryId).containsExactly(1L, 2L, 3L);
        assertThat(ranked.get(0).points()).isEqualTo(2);
        assertThat(ranked.get(0).legPoints()).containsExactly(1, 1, 3);
        assertThat(ranked.get(0).counted()).containsExactly(true, true, false);
        assertThat(ranked.get(1).points()).isEqualTo(3);
    }

    @Test
    void aTieOnPointsGoesToTheBestSingleLegThenTheLaps() {
        // Driver 1 is 1st then 3rd, driver 2 is 2nd twice, driver 3 is 3rd then 1st: all 4 points
        List<Map<Long, Run>> legs = List.of(
                Map.of(1L, run(10, 300_000), 2L, run(9, 300_000), 3L, run(8, 300_000)),
                Map.of(1L, run(7, 300_000), 2L, run(9, 300_000), 3L, run(10, 300_000)));

        List<Standing> ranked = FinalLegScoring.rank(legs, List.of(1L, 2L, 3L), 2);

        assertThat(ranked).extracting(Standing::points).containsExactly(4, 4, 4);
        // 1 + 3 beats 2 + 2; between the two 1 + 3s, driver 3 has more laps (18 against 17)
        assertThat(ranked).extracting(Standing::entryId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void identicalLegPointsAreSeparatedByTheLapsThenTheTimeOfTheCountingLegs() {
        // Both score 1st + 2nd on the same laps, so the combined time decides
        List<Map<Long, Run>> legs = List.of(
                Map.of(1L, run(10, 300_000), 2L, run(10, 301_000)),
                Map.of(1L, run(10, 302_000), 2L, run(10, 300_500)));

        List<Standing> ranked = FinalLegScoring.rank(legs, List.of(1L, 2L), 2);

        // 1 + 2 each; same laps; driver 1 has 602,000 ms and driver 2 has 601,500 ms
        assertThat(ranked).extracting(Standing::entryId).containsExactly(2L, 1L);
    }

    @Test
    void aDriverWhoDidNotRunALegScoresTheFieldPlusOneForIt() {
        List<Map<Long, Run>> legs = List.of(
                Map.of(1L, run(10, 300_000), 2L, run(9, 300_000)),
                Map.of(1L, run(10, 300_000)));

        List<Standing> ranked = FinalLegScoring.rank(legs, List.of(1L, 2L), 0);

        assertThat(ranked.get(1).entryId()).isEqualTo(2L);
        assertThat(ranked.get(1).legPoints()).containsExactly(2, 3);
        assertThat(ranked.get(1).points()).isEqualTo(5);
    }

    @Test
    void onlyTheLegsRunSoFarCountWhenFewerThanTheCountingNumberHaveFinished() {
        List<Map<Long, Run>> legs = List.of(Map.of(1L, run(10, 300_000), 2L, run(9, 300_000)));

        List<Standing> ranked = FinalLegScoring.rank(legs, List.of(1L, 2L), 2);

        assertThat(ranked).extracting(Standing::points).containsExactly(1, 2);
    }
}
