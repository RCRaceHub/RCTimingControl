package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.csvimport.CsvImportResult;
import dev.monkeypatch.rctiming.domain.csvimport.CsvImportService;
import dev.monkeypatch.rctiming.domain.csvimport.RcTimingCsvParser;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Imports an RC-Timing style driver CSV into an event (#39), under {@code /api/v1/admin/events/{eventId}}. ADMIN
 * only. The RaceHub import and the entry feed are the other ways entries arrive.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}")
@AdminOnly
public class CsvImportController {

    private final CsvImportService importService;

    public CsvImportController(CsvImportService importService) {
        this.importService = importService;
    }

    /**
     * Previews the file with {@code dryRun=true}, saving nothing. Without it, creates the new rows
     * and applies only the picks: {@code update} names changed rows by key, {@code withdraw} names
     * missing entries by id. A blocked import returns 422 with the preview.
     *
     * <p>{@code POST /csv-import} as multipart form data with a {@code file} part. 404 for an unknown event. Walk-ins
     * are never withdrawn. Audited.
     */
    @Audited("audit_log")
    @PostMapping(value = "/csv-import", consumes = "multipart/form-data")
    public ResponseEntity<CsvImportResult> importCsv(@PathVariable Long eventId,
                                                     @RequestParam(defaultValue = "false") boolean dryRun,
                                                     @RequestPart("file") MultipartFile file,
                                                     @RequestParam(name = "update", required = false) List<String> update,
                                                     @RequestParam(name = "withdraw", required = false) List<Long> withdraw)
            throws IOException {
        String content = RcTimingCsvParser.decode(file.getBytes());
        var selection = new CsvImportService.Selection(
                update == null ? Set.of() : Set.copyOf(update), withdraw == null ? Set.of() : Set.copyOf(withdraw));
        CsvImportResult result =
                importService.importCsv(CurrentOfficial.actor(), eventId, content, dryRun, selection);
        return ImportResponses.of(result);
    }
}
