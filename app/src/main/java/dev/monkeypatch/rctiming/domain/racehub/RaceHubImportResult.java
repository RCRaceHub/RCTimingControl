package dev.monkeypatch.rctiming.domain.racehub;

import java.util.List;

/**
 * The preview of an import, and its outcome. {@code applied} is false for a dry run and for an
 * import that {@code blocked} (unmapped classes or invalid rows): nothing was saved.
 *
 * <p>{@code blocked} is also set on a dry run that a real import would have refused, so the page can show what to fix
 * before anyone tries. {@code revision} is the export's own revision number. {@code rows} says what happens to each
 * export entry; {@code summary} counts them by action.
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
        List<Row> rows) {

    public record Summary(int created, int updated, int withdrawn, int unchanged, int stale, int skipped) {
    }

    public record UnmappedClass(String racehubEventClassId, String rcClassName, String className, int entryCount) {
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
