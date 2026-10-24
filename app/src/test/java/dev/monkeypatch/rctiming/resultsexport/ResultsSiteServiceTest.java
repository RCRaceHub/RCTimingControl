package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.competitor.NameVisibility;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsSiteServiceTest {

    @Test
    void swapsInPublicNamesForRestrictedCompetitors() {
        var shown = new ResultsExportV1.Row(1, null, null, null, null, 1L, 10L, "Ada King", "3", 10, 1000L, null,
                List.of());
        var hidden = new ResultsExportV1.Row(2, null, null, null, null, 2L, 20L, "Young Driver", "7", 9, 1100L, null,
                List.of());
        var race = new ResultsExportV1.Race(1, 1, List.of(), "2WD", "QUALIFIER", 1, 1, null, "FINISHED", "t",
                List.of(shown, hidden));
        var standing = new ResultsExportV1.Standing(1, null, null, 20L, "Young Driver", 10, null, null, false, false);
        var championship = new ResultsExportV1.Championship(1, "Club", 1, List.of(
                new ResultsExportV1.ChampionshipClass(1, "2WD", List.of(standing))));
        var export = new ResultsExportV1(1, 1, "t", new ResultsExportV1.Source("RCTC", "Club"),
                new ResultsExportV1.EventRef(null, 1, "Round", "2026-10-24", "COMPLETED"), List.of(race),
                List.of(championship));

        var result = ResultsSiteService.withPublicNames(export,
                new PublicNames.Snapshot(Map.of(20L, NameVisibility.HIDE)));

        assertThat(result.races().get(0).results()).extracting(ResultsExportV1.Row::displayName)
                .containsExactly("Ada King", "Driver 7");
        assertThat(result.championships().get(0).classes().get(0).standings().get(0).displayName())
                .isEqualTo("Driver");
    }
}
