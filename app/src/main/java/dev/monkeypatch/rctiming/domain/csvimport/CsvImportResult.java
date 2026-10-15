package dev.monkeypatch.rctiming.domain.csvimport;

import java.util.List;
import java.util.Map;

/**
 * The preview of an RC-Timing CSV import, and its outcome (#39). {@code applied} is false for a
 * dry run and for an import that {@code blocked} (bad rows, unmapped classes, or a selection that
 * no longer matches the file): nothing was saved.
 *
 * <p>{@code blocked} is also set on a dry run that a real import would have refused, so the page can show what to fix
 * before anyone tries. Only new rows are created without being picked: a changed row is updated and a missing entry
 * withdrawn only when the official picked it.
 */
public record CsvImportResult(
        boolean dryRun,
        boolean blocked,
        boolean applied,
        Summary summary,
        List<UnmappedClass> unmappedClasses,
        List<String> errors,
        List<String> warnings,
        List<Row> rows) {

    /**
     * How many rows fall in each group, and what was applied: every new row, the changed rows
     * picked for update and the missing entries picked for withdrawal.
     */
    public record Summary(int newEntries, int changed, int unchanged, int missing, int skipped,
                          int created, int updated, int withdrawn) {
    }

    /**
     * A class the import couldn't place. {@code key} is what to map in the event's class mappings:
     * {@code CSV:} and the file's class name, or {@code CSV:#} and its class number.
     */
    public record UnmappedClass(String key, String className, Integer classNumber, int entryCount) {
    }

    /**
     * One row of the file, or one missing entry. {@code key} identifies a file row on confirm (pick
     * changed rows by it); {@code entryId} identifies a missing entry (pick it to withdraw it).
     * {@code line} is null for a missing entry. {@code info} holds RC-Timing columns RCTC doesn't
     * keep, such as Grade and Car Make, for the official to read.
     */
    public record Row(Group group, String key, Integer line, String name, Long brcaNumber, String className,
                      Integer classNumber, Long eventClassId, Long entryId, String primaryTransponder,
                      String secondaryTransponder, List<Change> changes, Map<String, String> info,
                      boolean applied, String reason) {
    }

    /** One field that differs between the entry in RCTC and the file. */
    public record Change(String field, String before, String after) {
    }

    public enum Group {
        /** Not in RCTC yet. Created on confirm. */
        NEW,
        /** In RCTC with different details. Updated on confirm only when picked. */
        CHANGED,
        /** In RCTC with the same details. */
        UNCHANGED,
        /** Made by an earlier CSV import but not in this file. Withdrawn on confirm only when picked. */
        MISSING,
        /** An {@code update} row: RC-Timing's archive only, so not booked in. */
        SKIPPED
    }
}
