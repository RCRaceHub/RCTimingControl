package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.PointsFinalsConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoFinalsSeedingServiceTest {

    private static BumpUpConfig bumpUp(int gridSize, int bumpSpots) {
        return new BumpUpConfig(3, 5, 2, gridSize, bumpSpots, StartType.STAGGER, StartType.GRID, null, 2, 1);
    }

    @Test
    void bumpUpFormat_usesItsGridSizeAndBumpSpots() {
        assertThat(AutoFinalsSeedingService.layout(bumpUp(10, 2), 18, 2)).containsExactly(10, 2);
    }

    @Test
    void bumpUpFormat_growsTheGridWhenTheFieldWouldNotFit() {
        // 10 + 1 * (10 - 2) = 18 places for 25 drivers
        int[] layout = AutoFinalsSeedingService.layout(bumpUp(10, 2), 25, 2);
        assertThat(layout[0] + (layout[0] - layout[1])).isGreaterThanOrEqualTo(25);
        assertThat(layout[1]).isEqualTo(2);
    }

    @Test
    void otherFormats_splitTheFieldEvenlyWithNoBumps() {
        var points = new PointsFinalsConfig(3, 2, 5, 5, StartType.STAGGER, StartType.GRID, null, 2, 1);
        assertThat(AutoFinalsSeedingService.layout(points, 17, 2)).containsExactly(9, 0);
    }
}
