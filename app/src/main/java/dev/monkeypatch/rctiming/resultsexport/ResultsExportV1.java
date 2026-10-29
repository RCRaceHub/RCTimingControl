package dev.monkeypatch.rctiming.resultsexport;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;

/**
 * Results Export v1 (#27): an event's results so far, as sent to RaceHub. The contract is documented in
 * {@code docs/results-export-v1.md} and {@code docs/results-export-v1.schema.json}.
 *
 * <p>Each export is the whole event so far, with a higher {@code revision} than the last, so a receiver keeps
 * the highest revision it has seen and can ignore a replay or a late, older one. Laps, contact details, dates of
 * birth and guardians are never included.
 *
 * @param schemaVersion always 1
 * @param revision      increases with every export of this event
 * @param generatedAt   when this export was built, ISO-8601 UTC
 * @param source        the club and system the results came from
 * @param event         the RaceHub event and the RCTC event
 * @param races         every finished race, in running order
 * @param championships each championship this event is a round of, with its standings after this event
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"schemaVersion", "revision", "generatedAt", "source", "event", "races", "championships"})
public record ResultsExportV1(
        int schemaVersion,
        long revision,
        String generatedAt,
        Source source,
        EventRef event,
        List<Race> races,
        List<Championship> championships) {

    public static final int SCHEMA_VERSION = 1;

    /** The system and the club. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"system", "clubName"})
    public record Source(String system, String clubName) {
    }

    /**
     * @param racehubEventId the RaceHub event id from the imported entry file; null for an event with no import
     * @param rctcEventId    the event's id in RCTC
     * @param date           the event date, YYYY-MM-DD
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"racehubEventId", "rctcEventId", "name", "date", "status"})
    public record EventRef(String racehubEventId, long rctcEventId, String name, String date, String status) {
    }

    /**
     * One finished race.
     *
     * @param racehubEventClassIds the RaceHub event_class_id values of the entries in the race (several when
     *                             classes are combined, none when every entry is a walk-in)
     * @param className            the RCTC racing class name, which RaceHub calls rc_class_name
     * @param roundType            PRACTICE, QUALIFIER or FINAL
     * @param finalLetter          A, B, ... for a final; null otherwise
     * @param status               FINISHED, or ABANDONED when race control abandoned it
     * @param finishedAt           ISO-8601 UTC
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"rctcRaceId", "rctcEventClassId", "racehubEventClassIds", "className", "roundType",
            "roundNumber", "heatNumber", "finalLetter", "status", "finishedAt", "results"})
    public record Race(
            long rctcRaceId,
            long rctcEventClassId,
            List<String> racehubEventClassIds,
            String className,
            String roundType,
            int roundNumber,
            int heatNumber,
            String finalLetter,
            String status,
            String finishedAt,
            List<Row> results) {
    }

    /**
     * One driver's result in a race. Imported entries carry RaceHub's {@code entry_id} and
     * {@code driver_profile_id}; walk-ins have {@code external_source} null and only RCTC ids.
     *
     * @param externalSource RACEHUB for an imported entry, null for a walk-in
     * @param totalTimeMs    from the start to the driver's last crossing
     * @param bestLapMs      null when the driver completed no lap
     * @param penalties      penalties the referee gave the driver in this race
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"position", "externalSource", "entryId", "driverProfileId", "racehubEventClassId",
            "rctcEntryId", "rctcCompetitorId", "displayName", "carNumber", "laps", "totalTimeMs", "bestLapMs",
            "penalties"})
    public record Row(
            int position,
            String externalSource,
            String entryId,
            String driverProfileId,
            String racehubEventClassId,
            Long rctcEntryId,
            Long rctcCompetitorId,
            String displayName,
            String carNumber,
            int laps,
            long totalTimeMs,
            Long bestLapMs,
            List<Penalty> penalties) {
    }

    /**
     * @param type             LAP (laps taken off) or TIME (seconds added)
     * @param value            laps or seconds
     * @param includedInResult whether the row's laps, time and position already allow for it: true for any
     *                         penalty given since the race last started (#63), false for one left over from
     *                         a run that was restarted. For a result stored before #63 and not corrected
     *                         since, only a LAP penalty given while the race ran is included.
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"type", "value", "reason", "includedInResult"})
    public record Penalty(String type, BigDecimal value, String reason, boolean includedInResult) {
    }

    /**
     * A championship this event is a round of.
     *
     * @param roundNumber this event's round in the championship
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"rctcChampionshipId", "name", "roundNumber", "classes"})
    public record Championship(long rctcChampionshipId, String name, int roundNumber, List<ChampionshipClass> classes) {
    }

    /** The standings of one class, best first. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"rctcRacingClassId", "className", "standings"})
    public record ChampionshipClass(long rctcRacingClassId, String className, List<Standing> standings) {
    }

    /**
     * One driver's place in a class's standings after this event.
     *
     * @param position      shared by drivers on equal points
     * @param totalPoints   best-X-from-Y total, with bonuses
     * @param roundPosition the driver's finishing position at this event; null if they did not score here
     * @param roundPoints   points scored at this event; null if they did not score here
     * @param roundDropped  whether this event's score is one of the driver's dropped rounds
     * @param roundExcluded whether an admin excluded the driver from this round
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonPropertyOrder({"position", "externalSource", "driverProfileId", "rctcCompetitorId", "displayName",
            "totalPoints", "roundPosition", "roundPoints", "roundDropped", "roundExcluded"})
    public record Standing(
            int position,
            String externalSource,
            String driverProfileId,
            long rctcCompetitorId,
            String displayName,
            int totalPoints,
            Integer roundPosition,
            Integer roundPoints,
            boolean roundDropped,
            boolean roundExcluded) {
    }
}
