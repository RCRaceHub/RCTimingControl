package dev.monkeypatch.rctiming.livefeed;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Live Feed v1 (#28): one message per race update, sent to the relay as a WebSocket text frame. See
 * {@code docs/live-feed-v1.md} and {@code resources/livefeed/live-feed-v1.schema.json}.
 *
 * <p>People appear by display name only: no entry, competitor or transponder ids, and nothing personal.
 *
 * @param schemaVersion always {@link #SCHEMA_VERSION}
 * @param type          always {@code race}
 * @param sequence      counts up across every message this app sends, so a viewer can drop one that arrives late
 * @param sentAt        ISO-8601 UTC
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"schemaVersion", "type", "sequence", "sentAt", "event", "race", "standings"})
public record LiveFeedV1(
        int schemaVersion,
        String type,
        long sequence,
        String sentAt,
        Event event,
        Race race,
        List<Standing> standings) {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE_RACE = "race";

    /** @param date ISO-8601 date */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"rctcEventId", "name", "date"})
    public record Event(long rctcEventId, String name, String date) {
    }

    /**
     * @param roundType   PRACTICE, QUALIFIER or FINAL
     * @param finalLetter A, B, ... for a final; null otherwise
     * @param status      GRID, RUNNING, STOPPED or FINISHED; PENDING if race control restarted it
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"rctcRaceId", "className", "roundType", "roundNumber", "heatNumber", "finalLetter", "status",
            "clock"})
    public record Race(
            long rctcRaceId,
            String className,
            String roundType,
            int roundNumber,
            int heatNumber,
            String finalLetter,
            String status,
            Clock clock) {
    }

    /**
     * The race clock when the message was sent. A viewer can count on from {@code elapsedMs} while
     * {@code running} is true.
     *
     * @param elapsedMs   race time so far, not counting time stopped
     * @param durationMs  the race's length from its format, or null if the format doesn't say
     * @param remainingMs {@code durationMs - elapsedMs}, never below 0, or null without a duration
     * @param running     whether the clock is counting
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"elapsedMs", "durationMs", "remainingMs", "running"})
    public record Clock(long elapsedMs, Long durationMs, Long remainingMs, boolean running) {
    }

    /**
     * One car in the running order.
     *
     * @param carNumber     the number on the car, or null
     * @param gapToLeaderMs time behind the leader on the same lap; null for the leader and for cars laps down
     * @param gapToAheadMs  time behind the car ahead on the same lap; null for the leader and when laps differ
     * @param lapsDown      laps behind the leader
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"position", "displayName", "carNumber", "laps", "lastLapMs", "bestLapMs", "gapToLeaderMs",
            "gapToAheadMs", "lapsDown"})
    public record Standing(
            int position,
            String displayName,
            Integer carNumber,
            int laps,
            Long lastLapMs,
            Long bestLapMs,
            Long gapToLeaderMs,
            Long gapToAheadMs,
            int lapsDown) {
    }
}
