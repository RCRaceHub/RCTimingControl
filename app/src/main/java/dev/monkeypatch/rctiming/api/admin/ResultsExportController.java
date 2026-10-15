package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportService;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportV1;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Results sent to RaceHub (#27): what is queued and sent, retry now, and an event's results file. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class ResultsExportController {

    private static final int OUTBOX_ROWS = 100;

    private final ResultsExportService exportService;
    private final ResultsOutboxQuery outboxQuery;
    private final RaceHubResultsEndpoint endpoint;
    private final ClubProfileService clubProfileService;

    public ResultsExportController(ResultsExportService exportService, ResultsOutboxQuery outboxQuery,
                                   RaceHubResultsEndpoint endpoint, ClubProfileService clubProfileService) {
        this.exportService = exportService;
        this.outboxQuery = outboxQuery;
        this.endpoint = endpoint;
        this.clubProfileService = clubProfileService;
    }

    /**
     * @param sendingEnabled  whether the RaceHub address and key are both set; without them, exports wait
     * @param resultsUrl      the address in use, or null
     * @param savedUrl        the address set here, or null when the settings file's is in use
     * @param missingSettings what is still needed before anything is sent
     * @param exports         the newest exports first
     */
    public record ResultsExportsDto(boolean sendingEnabled, String resultsUrl, String savedUrl,
                                    List<String> missingSettings,
                                    List<ResultsOutboxQuery.OutboxRow> exports) {
    }

    @GetMapping("/results-exports")
    public ResultsExportsDto list() {
        var settings = endpoint.snapshot();
        return new ResultsExportsDto(settings.sendingEnabled(),
                settings.resultsUrl() == null ? null : settings.resultsUrl().toString(),
                settings.savedUrl(),
                settings.missingSettings(),
                outboxQuery.latest(OUTBOX_ROWS));
    }

    /** @param resultsUrl the RaceHub address; blank clears it, so the settings file's applies */
    public record ResultsUrlRequest(@Size(max = 500) String resultsUrl) {
    }

    @Audited("audit_log")
    @PutMapping("/results-exports/address")
    public ResultsExportsDto setAddress(Authentication auth, @RequestBody @Valid ResultsUrlRequest request) {
        if (request.resultsUrl() != null && !request.resultsUrl().isBlank()) {
            RaceHubResultsEndpoint.parse(request.resultsUrl());
        }
        clubProfileService.updateRaceHubResultsUrl(CurrentOfficial.actor(auth), request.resultsUrl());
        return list();
    }

    @Audited("audit_log")
    @PostMapping("/results-exports/{id}/retry")
    public ResponseEntity<Void> retry(Authentication auth, @PathVariable long id) {
        exportService.retryNow(CurrentOfficial.actor(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** The event's results as they stand now, as a Results Export v1 file. */
    @GetMapping("/events/{eventId}/results-export")
    public ResponseEntity<ResultsExportV1> download(@PathVariable long eventId) {
        ResultsExportV1 export = exportService.current(eventId);
        String filename = "results-event-" + eventId + "-r" + export.revision() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(export);
    }
}
