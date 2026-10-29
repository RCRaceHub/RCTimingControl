package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.csvimport.CsvImportResult;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubImportResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * How the entry import endpoints answer. An import that something blocks (see the result's errors and unmapped
 * classes) saves nothing and returns 422 with the preview, so the page can show what to fix. A dry run is a
 * preview, so it is 200 even when blocked, as is every import that went through.
 */
final class ImportResponses {

    private ImportResponses() {
    }

    /** The answer for a RaceHub or feed import: 422 only for a real import that was blocked, else 200. */
    static ResponseEntity<RaceHubImportResult> of(RaceHubImportResult result) {
        return answer(result, result.blocked() && !result.dryRun());
    }

    /** The answer for a CSV import: 422 only for a real import that was blocked, else 200. */
    static ResponseEntity<CsvImportResult> of(CsvImportResult result) {
        return answer(result, result.blocked() && !result.dryRun());
    }

    private static <T> ResponseEntity<T> answer(T result, boolean refused) {
        return ResponseEntity.status(refused ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.OK).body(result);
    }
}
