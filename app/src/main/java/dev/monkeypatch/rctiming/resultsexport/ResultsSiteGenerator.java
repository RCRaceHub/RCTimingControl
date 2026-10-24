package dev.monkeypatch.rctiming.resultsexport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Results Export v1 documents into a folder of static web pages a club can upload to its own web space (#359).
 * This is the "results site" choice of the publish setting (#319) for clubs without RaceHub: no server, no accounts,
 * and nothing leaves the laptop except what the club uploads itself.
 *
 * <p>Pure: no Spring, database or clock, so it is tested on plain documents. It is called by
 * {@link ResultsSiteService} on a scheduler or request thread. The pages carry only what the export carries: display
 * names (which follow each competitor's public name setting), positions, laps, times and penalties. No lap-by-lap
 * times, contact details or dates of birth ever reach it. {@link ResultsSiteService} swaps the export's names for the
 * public ones first. All text is HTML-escaped, and the pages use inline CSS and
 * no scripts, so they open from a file, print on A4 and work on any web host.
 *
 * <p>Output paths are relative and use {@code /}: {@code index.html} and {@code events/<rctcEventId>.html}.
 */
public final class ResultsSiteGenerator {

    static final String INDEX = "index.html";

    private static final String STYLE = """
            body{font-family:system-ui,sans-serif;margin:0 auto;max-width:56rem;padding:1rem;color:#111}
            h1{font-size:1.5rem;margin:.2rem 0}h2{font-size:1.2rem;margin:1.5rem 0 .3rem;border-bottom:2px solid #333}
            h3{font-size:1rem;margin:1rem 0 .3rem}table{border-collapse:collapse;width:100%;margin-bottom:.5rem}
            th,td{border-bottom:1px solid #ddd;padding:.25rem .5rem;text-align:left}
            td.n,th.n{text-align:right;font-variant-numeric:tabular-nums}s{color:#888}
            .muted{color:#555;font-size:.85rem}a{color:#0645ad}
            @media print{body{max-width:none}section.class{page-break-before:always}a{color:inherit;text-decoration:none}}
            """;

    private ResultsSiteGenerator() {
    }

    /**
     * Builds every page of the site.
     *
     * @param events   one export per event; events with no finished race are left out
     * @param clubName shown in the page headings; may be null
     * @return the pages by relative path, in a stable order
     */
    public static Map<String, String> generate(List<ResultsExportV1> events, String clubName) {
        List<ResultsExportV1> shown = events.stream()
                .filter(e -> !e.races().isEmpty())
                .sorted(Comparator.comparing((ResultsExportV1 e) -> e.event().date()).reversed()
                        .thenComparing(e -> e.event().rctcEventId(), Comparator.reverseOrder()))
                .toList();
        String club = clubName == null || clubName.isBlank() ? "Race results" : clubName;
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put(INDEX, indexPage(shown, club));
        for (ResultsExportV1 event : shown) {
            pages.put(eventPath(event), eventPage(event, club));
        }
        return pages;
    }

    static String eventPath(ResultsExportV1 event) {
        return "events/" + event.event().rctcEventId() + ".html";
    }

    private static String indexPage(List<ResultsExportV1> events, String club) {
        StringBuilder html = new StringBuilder();
        head(html, club);
        html.append("<h1>").append(esc(club)).append("</h1>\n");
        if (events.isEmpty()) {
            html.append("<p>No results yet.</p>\n");
        } else {
            html.append("<ul>\n");
            for (ResultsExportV1 e : events) {
                html.append("<li><a href=\"").append(eventPath(e)).append("\">").append(esc(e.event().name()))
                        .append("</a> <span class=\"muted\">").append(esc(e.event().date())).append("</span></li>\n");
            }
            html.append("</ul>\n");
        }
        html.append("</body></html>\n");
        return html.toString();
    }

    private static String eventPage(ResultsExportV1 event, String club) {
        StringBuilder html = new StringBuilder();
        head(html, event.event().name() + " - " + club);
        html.append("<p class=\"muted\"><a href=\"../index.html\">").append(esc(club)).append("</a></p>\n");
        html.append("<h1>").append(esc(event.event().name())).append("</h1>\n");
        html.append("<p class=\"muted\">").append(esc(event.event().date())).append(" &middot; results so far, updated ")
                .append(esc(event.generatedAt())).append("</p>\n");

        Map<String, List<ResultsExportV1.Race>> byClass = new LinkedHashMap<>();
        for (ResultsExportV1.Race race : event.races()) {
            byClass.computeIfAbsent(race.className(), k -> new ArrayList<>()).add(race);
        }
        for (Map.Entry<String, List<ResultsExportV1.Race>> cls : byClass.entrySet()) {
            html.append("<section class=\"class\"><h2>").append(esc(cls.getKey())).append("</h2>\n");
            for (ResultsExportV1.Race race : cls.getValue()) {
                racePart(html, race);
            }
            for (ResultsExportV1.Championship championship : event.championships()) {
                championshipPart(html, championship, cls.getKey());
            }
            html.append("</section>\n");
        }
        html.append("</body></html>\n");
        return html.toString();
    }

    private static void racePart(StringBuilder html, ResultsExportV1.Race race) {
        html.append("<h3>").append(esc(raceTitle(race)));
        if ("ABANDONED".equals(race.status())) {
            html.append(" (abandoned)");
        }
        html.append("</h3>\n<table><tr><th class=\"n\">Pos</th><th class=\"n\">Car</th><th>Driver</th>"
                + "<th class=\"n\">Laps</th><th class=\"n\">Time</th><th class=\"n\">Best lap</th>"
                + "<th>Penalties</th></tr>\n");
        for (ResultsExportV1.Row row : race.results()) {
            html.append("<tr><td class=\"n\">").append(row.position())
                    .append("</td><td class=\"n\">").append(esc(row.carNumber()))
                    .append("</td><td>").append(esc(row.displayName()))
                    .append("</td><td class=\"n\">").append(row.laps())
                    .append("</td><td class=\"n\">").append(time(row.totalTimeMs()))
                    .append("</td><td class=\"n\">").append(row.bestLapMs() == null ? "" : time(row.bestLapMs()))
                    .append("</td><td>").append(esc(penalties(row.penalties()))).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private static void championshipPart(StringBuilder html, ResultsExportV1.Championship championship,
                                         String className) {
        for (ResultsExportV1.ChampionshipClass cls : championship.classes()) {
            if (!cls.className().equals(className)) {
                continue;
            }
            html.append("<h3>").append(esc(championship.name())).append(" after round ")
                    .append(championship.roundNumber()).append("</h3>\n")
                    .append("<table><tr><th class=\"n\">Pos</th><th>Driver</th><th class=\"n\">Points</th>"
                            + "<th class=\"n\">This round</th></tr>\n");
            for (ResultsExportV1.Standing s : cls.standings()) {
                String round = s.roundPoints() == null ? "" : String.valueOf(s.roundPoints());
                if (s.roundDropped() && s.roundPoints() != null) {
                    round = "<s>" + round + "</s>";
                }
                html.append("<tr><td class=\"n\">").append(s.position())
                        .append("</td><td>").append(esc(s.displayName()))
                        .append("</td><td class=\"n\">").append(s.totalPoints())
                        .append("</td><td class=\"n\">").append(round).append("</td></tr>\n");
            }
            html.append("</table>\n");
        }
    }

    static String raceTitle(ResultsExportV1.Race race) {
        return switch (race.roundType()) {
            case "QUALIFIER" -> "Qualifying round " + race.roundNumber() + ", heat " + race.heatNumber();
            case "FINAL" -> race.finalLetter() == null ? "Final" : race.finalLetter() + " final";
            case "PRACTICE" -> "Practice";
            default -> race.roundType();
        };
    }

    /** {@code m:ss.mmm}, or {@code s.mmm} under a minute; the export sends milliseconds. */
    static String time(long millis) {
        long minutes = millis / 60_000;
        long seconds = millis / 1000 % 60;
        long ms = millis % 1000;
        return minutes > 0 ? "%d:%02d.%03d".formatted(minutes, seconds, ms) : "%d.%03d".formatted(seconds, ms);
    }

    private static String penalties(List<ResultsExportV1.Penalty> penalties) {
        List<String> parts = new ArrayList<>();
        for (ResultsExportV1.Penalty p : penalties) {
            parts.add(("LAP".equals(p.type()) ? "-" + p.value().stripTrailingZeros().toPlainString() + " lap"
                    : "+" + p.value().stripTrailingZeros().toPlainString() + " s")
                    + (p.reason() == null || p.reason().isBlank() ? "" : " (" + p.reason() + ")"));
        }
        return String.join(", ", parts);
    }

    private static void head(StringBuilder html, String title) {
        html.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
                .append(esc(title)).append("</title><style>").append(STYLE).append("</style></head><body>\n");
    }

    static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
