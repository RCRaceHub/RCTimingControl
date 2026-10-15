package dev.monkeypatch.rctiming.query.racecontrol;

/**
 * One entry as the transponder swap tool sees it (L11).
 *
 * @param competitorId the competitor, so the tool can fix how their name is said (#119)
 * @param spokenName   the admin's override for how the name is said aloud, or null for none
 * @param speechName   what the announcer says for them: the spoken name, else the display name tidied (#120)
 * @param importedTransponderNumber the imported file's primary, when it differs from a number swapped on the
 *                                  day (#50); null otherwise
 * @param importedSecondaryTransponderNumber the same for the secondary
 */
public record EntrySearchDto(
        long entryId,
        String competitorName,
        Long competitorId,
        String spokenName,
        String speechName,
        String className,
        String transponderNumber,
        String secondaryTransponderNumber,
        String importedTransponderNumber,
        String importedSecondaryTransponderNumber
) {}
