package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.PointsFinalsConfig;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoFinalsSeedingServiceTest {

    private static BumpUpConfig bumpUp(int gridSize, int bumpSpots) {
        return new BumpUpConfig(3, 5, 2, gridSize, bumpSpots, StartType.STAGGER, StartType.GRID, null, 2, 1);
    }

    @Test
    void bumpUpFormat_usesItsGridSizeAndBumpSpots() {
        assertThat(AutoFinalsSeedingService.layout(bumpUp(10, 2), 18, 2, null)).containsExactly(10, 2);
    }

    @Test
    void bumpUpFormat_growsTheGridWhenTheFieldWouldNotFit() {
        // 10 + 1 * (10 - 2) = 18 places for 25 drivers
        int[] layout = AutoFinalsSeedingService.layout(bumpUp(10, 2), 25, 2, null);
        assertThat(layout[0] + (layout[0] - layout[1])).isGreaterThanOrEqualTo(25);
        assertThat(layout[1]).isEqualTo(2);
    }

    @Test
    void otherFormats_splitTheFieldEvenlyWithNoBumps() {
        var points = new PointsFinalsConfig(3, 2, 5, 5, StartType.STAGGER, StartType.GRID, null, 2, 1);
        assertThat(AutoFinalsSeedingService.layout(points, 17, 2, null)).containsExactly(9, 0);
    }

    @Test
    void shikabaneMains_useTheirGridSizeAndPromotionSpots() {
        var shikabane = new ShikabaneConfig(5, StartType.GRID, 3, 3, 1, 3, 3, 8, 2, false);
        assertThat(AutoFinalsSeedingService.layout(shikabane, 20, 3, null)).containsExactly(8, 2);
    }

    @Test
    void shikabaneWithoutMainsGridSize_splitsTheFieldEvenly() {
        var shikabane = new ShikabaneConfig(5, StartType.GRID, 3, 3, 1, 3, 2, 0, 0, false);
        assertThat(AutoFinalsSeedingService.layout(shikabane, 17, 2, null)).containsExactly(9, 0);
    }

    @Test
    void anOfficialsCarsPerFinalReplacesTheGridSizeAndGrowsToFitTheField() {
        assertThat(AutoFinalsSeedingService.layout(bumpUp(10, 2), 18, 2, 5)).containsExactly(10, 2);
        assertThat(AutoFinalsSeedingService.layout(bumpUp(10, 2), 12, 2, 8)).containsExactly(8, 2);
        var points = new PointsFinalsConfig(3, 2, 5, 5, StartType.STAGGER, StartType.GRID, null, 2, 1);
        assertThat(AutoFinalsSeedingService.layout(points, 9, 2, 4)).containsExactly(5, 0);
        assertThat(AutoFinalsSeedingService.layout(points, 9, 2, 7)).containsExactly(7, 0);
    }

    @Test
    void aPointsFinalsFormatCanFixTheCarsPerFinal() {
        var fixed = new PointsFinalsConfig(4, 3, 5, 5, StartType.STAGGER, StartType.GRID, null, 2, 1, 2, false, false,
                390, 10, true);
        // 25 drivers, three finals: ten cars each fits, and the grid grows if the field would not
        assertThat(AutoFinalsSeedingService.layout(fixed, 25, 3, null)).containsExactly(10, 0);
        assertThat(AutoFinalsSeedingService.layout(fixed, 35, 3, null)).containsExactly(12, 0);
    }
}
