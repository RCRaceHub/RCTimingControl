package dev.monkeypatch.rctiming.query.competitor;

/**
 * The transponder numbers a competitor raced with in their most recent entry, offered as the starting point when an
 * official adds them to another event. Any number may be null; all four are null for a competitor with no entries.
 */
public record CompetitorTransponderDto(
        String primaryTransponder,
        String secondaryTransponder,
        String thirdTransponder,
        String fourthTransponder) {
}
