package dev.monkeypatch.rctiming.resultsexport;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultsSiteGeneratorTest {

    private static ResultsExportV1 export(long id, String name, String date, List<ResultsExportV1.Race> races) {
        return new ResultsExportV1(1, 1, "2026-10-24T10:00:00Z", new ResultsExportV1.Source("RCTC", "Test Club"),
                new ResultsExportV1.EventRef(null, id, name, date, "COMPLETED"), races, List.of());
    }

    private static ResultsExportV1.Race race(String className, String type, int heat, String letter,
                                             List<ResultsExportV1.Row> rows) {
        return new ResultsExportV1.Race(10, 1, List.of(), className, type, 1, heat, letter, "FINISHED",
                "2026-10-24T10:00:00Z", rows);
    }

    private static ResultsExportV1.Row row(int position, String name, Long bestLapMs,
                                           List<ResultsExportV1.Penalty> penalties) {
        return new ResultsExportV1.Row(position, null, null, null, null, 1L, 2L, name, "7", 12, 305_123L, bestLapMs,
                penalties);
    }

    @Test
    void listsEventsNewestFirstAndSkipsEventsWithNoFinishedRace() {
        Map<String, String> pages = ResultsSiteGenerator.generate(List.of(
                export(1, "Old round", "2026-09-01", List.of(race("2WD", "QUALIFIER", 1, null, List.of()))),
                export(2, "New round", "2026-10-01", List.of(race("2WD", "QUALIFIER", 1, null, List.of()))),
                export(3, "Empty", "2026-10-20", List.of())), "Test Club");

        assertThat(pages.keySet()).containsExactly("index.html", "events/2.html", "events/1.html");
        String index = pages.get("index.html");
        assertThat(index.indexOf("New round")).isLessThan(index.indexOf("Old round"));
        assertThat(index).doesNotContain("Empty");
    }

    @Test
    void showsResultsTimesAndPenaltiesAndEscapesNames() {
        var penalty = new ResultsExportV1.Penalty("LAP", new BigDecimal("1.0"), "Cut <track>", true);
        var event = export(5, "Round & Round", "2026-10-24", List.of(
                race("2WD", "QUALIFIER", 2, null, List.of(row(1, "A <b>Driver</b>", 17_456L, List.of(penalty)))),
                race("2WD", "FINAL", 1, "A", List.of(row(1, "Fin Winner", null, List.of())))));

        String html = ResultsSiteGenerator.generate(List.of(event), "Test Club").get("events/5.html");

        assertThat(html).contains("Qualifying round 1, heat 2", "A final", "5:05.123", "17.456", "-1 lap (Cut &lt;track&gt;)");
        assertThat(html).contains("A &lt;b&gt;Driver&lt;/b&gt;", "Round &amp; Round").doesNotContain("<b>Driver");
    }

    @Test
    void formatsTimes() {
        assertThat(ResultsSiteGenerator.time(59_001)).isEqualTo("59.001");
        assertThat(ResultsSiteGenerator.time(60_000)).isEqualTo("1:00.000");
    }
}
