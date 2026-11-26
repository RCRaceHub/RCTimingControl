package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.livefeed.LiveFeedV1;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Live Results v1: the snapshot of one running race that RCTC posts to RaceHub's {@code /api/rctc/v1/live} while the
 * race is on the grid, running or stopped, so RaceHub can show the standings as they happen. See
 * {@code docs/live-results-v1.md}.
 *
 * <p>Every snapshot is {@code provisional}: it is the race as timed so far, before any penalty or correction. The
 * finished race still arrives through Results Export v1, which is authoritative and replaces whatever live view
 * RaceHub holds. A snapshot is the whole race, not a change, so RaceHub keeps only the one with the highest
 * {@code sequence}. People appear by display name only, as on the live feed (#265).
 *
 * @param schemaVersion  always {@link #SCHEMA_VERSION}
 * @param type           always {@link #TYPE}
 * @param sequence       counts up across every snapshot this app sends and starts from the clock, so it keeps rising
 *                       over a restart; RaceHub drops a snapshot whose sequence is not higher than the one it holds
 * @param sentAt         ISO-8601 UTC
 * @param provisional    always true
 * @param racehubEventId RaceHub's id for the event, from the entry import
 * @param event          the RCTC event
 * @param race           the race and its status and clock
 * @param standings      the running order, then cars on the grid yet to cross the line
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LiveResultsV1(
        int schemaVersion,
        String type,
        long sequence,
        String sentAt,
        boolean provisional,
        String racehubEventId,
        LiveFeedV1.Event event,
        LiveFeedV1.Race race,
        List<LiveFeedV1.Standing> standings) {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE = "live_results";
}
