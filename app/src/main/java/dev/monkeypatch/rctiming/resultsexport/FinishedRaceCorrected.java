package dev.monkeypatch.rctiming.resultsexport;

/**
 * Published when a referee or marshal changes a race that has already finished (#27). Two listeners react, both
 * synchronously inside the correcting request's transaction: {@code ResultSnapshotService} works the stored result
 * out again, and {@link ResultsExportTriggers} asks for a new export, so the export is built from the corrected result.
 */
public record FinishedRaceCorrected(long raceId) {
}
