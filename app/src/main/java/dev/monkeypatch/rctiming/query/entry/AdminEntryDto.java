package dev.monkeypatch.rctiming.query.entry;

import java.time.Instant;

/**
 * One entry in a class as an official sees it ({@link AdminEntryQueryService}).
 *
 * @param displayName                       the competitor's display name (L5); null if the competitor row is missing
 * @param transponderNumber                 the primary transponder number for this meeting
 * @param secondaryTransponderNumber        the optional secondary number, or null
 * @param importedTransponderNumber         the number in the imported file where a swap on the day changed it (#50),
 *                                          else null
 * @param importedSecondaryTransponderNumber the same for the secondary number
 * @param status                            the entry's status name
 * @param submittedAt                       when the entry was made or imported
 * @param withdrawnAt                       when it was withdrawn, or null
 */
public record AdminEntryDto(
        Long id,
        Long competitorId,
        String displayName,   // competitor display name (L5)
        String transponderNumber,
        String secondaryTransponderNumber,
        String importedTransponderNumber,          // the imported file's number where a swap on the day differs (#50)
        String importedSecondaryTransponderNumber,
        String status,
        Instant submittedAt,
        Instant withdrawnAt) {
}
