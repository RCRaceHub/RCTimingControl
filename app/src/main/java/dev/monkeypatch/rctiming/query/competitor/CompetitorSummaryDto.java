package dev.monkeypatch.rctiming.query.competitor;

/** A competitor as officials see it in pickers and lists (L5, #13). */
public record CompetitorSummaryDto(
        Long id,
        String displayName,
        String brcaNumber,
        String homeClub,
        /** The admin's override for how the name is said aloud, or null for none (#119). */
        String spokenName,
        /** What the announcer says: the spoken name when set, else the display name tidied for speech (#120). */
        String speechName,
        /** How the name is shown on public screens: SHOW, INITIALS or HIDE (#265). */
        String nameVisibility) {
}
