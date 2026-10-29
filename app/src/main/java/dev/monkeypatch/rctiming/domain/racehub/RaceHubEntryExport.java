package dev.monkeypatch.rctiming.domain.racehub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * RaceHub Entry Export v1 (L7, #15). Only the fields the export carries are read. Anything else
 * in the document, such as a field added in a later minor version, is ignored, so contact,
 * date of birth, guardian or payment data can never be accepted.
 *
 * <p>Any booking system can produce it: the published schema is
 * {@code resources/racehub/entry-export-v1.schema.json}. {@code source} names the system the ids come
 * from and defaults to RACEHUB (#41).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RaceHubEntryExport(
        Integer schemaVersion,
        String source,
        ExportEvent event,
        Long revision,
        List<ExportEntry> entries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExportEvent(
            String id,
            String externalReference,
            String name,
            String eventLocalDate,
            String timezone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ExportEntry(
            String entryId,
            Long entryVersion,
            String entryStatus,
            String raceDayStatus,
            String eventClassId,
            String rcClassName,
            String rcClassNumber,
            String className,
            String driverProfileId,
            String driverDisplayName,
            String brcaNumber,
            String brcaSource,
            String homeClub,
            String ageCategory,
            String grade,
            String primaryTransponder,
            String secondaryTransponder,
            Boolean clubCarRequested,
            String clubCarName) {
    }
}
