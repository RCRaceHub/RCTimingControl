package dev.monkeypatch.rctiming.query.entry;

import java.time.Instant;

public record AdminEntryDto(
        Long id,
        Long userId,          // racer login from the old racer portal (#18), null on newer entries
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
