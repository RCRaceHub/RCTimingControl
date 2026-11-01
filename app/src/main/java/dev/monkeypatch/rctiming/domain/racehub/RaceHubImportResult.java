package dev.monkeypatch.rctiming.domain.racehub;

import dev.monkeypatch.rctiming.domain.media.PhotoLink;

import java.util.List;

/**
 * The preview of an import, and its outcome. {@code applied} is false for a dry run and for an
 * import that {@code blocked} (classes with no club link, or invalid rows): nothing was saved.
 *
 * <p>{@code blocked} is also set on a dry run that a real import would have refused, so the page can show what to fix
 * before anyone tries. {@code revision} is the export's own revision number. {@code rows} says what happens to each
 * export entry; {@code summary} counts them by action. {@code photoLinks} are the headshot and car photo addresses of
 * the entries that were created, updated or found unchanged (#699), empty for a dry run and a blocked import; they are not fetched by
 * the import but by {@code PhotoImportService} afterwards.
 */
public record RaceHubImportResult(
        boolean dryRun,
        boolean blocked,
        boolean applied,
        String racehubEventName,
        Long revision,
        Summary summary,
        List<UnmappedClass> unmappedClasses,
        List<String> errors,
        List<String> warnings,
        List<Row> rows,
        List<PhotoLink> photoLinks) {

    /** How many export entries fall under each {@link Action}. */
    public record Summary(int created, int updated, int withdrawn, int unchanged, int stale, int skipped) {
    }

    /**
     * A class in the file that no event class here resolves to, and how many entries are in it. Linking it unblocks the
     * import. {@code classKey} is the club class id (for another source, {@code <SOURCE>:<club class id>}).
     * {@code suggestedRacingClassId} is the racing class the club linked it to, when that class is not in this event
     * yet (or is in it more than once), so the screen can offer to add it; null when the club has made no link.
     */
    public record UnmappedClass(String classKey, String rcClassName, String className, int entryCount,
                                Long suggestedRacingClassId) {
    }

    /** One export entry and what the import does with it. */
    public record Row(String entryId, Long entryVersion, String driverDisplayName, Action action,
                      Long eventClassId, Long rctcEntryId) {
    }

    public enum Action {
        /** New entry. */
        CREATE,
        /** Newer version of an entry already here. */
        UPDATE,
        /** Newer version that withdraws the entry. The entry and its race history are kept. */
        WITHDRAW,
        /** Same version as already applied (a replay). */
        UNCHANGED,
        /** Older version than already applied. Ignored. */
        STALE,
        /** Withdrawn in RaceHub and never imported here. Nothing to do. */
        SKIP
    }
}
