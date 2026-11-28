package dev.monkeypatch.rctiming.api.resultsexport;

import dev.monkeypatch.rctiming.api.resultsexport.dto.ResultsExportsDto;
import dev.monkeypatch.rctiming.api.resultsexport.dto.PublishTargetRequest;
import dev.monkeypatch.rctiming.api.resultsexport.dto.ReuploadResponse;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportService;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportV1;
import dev.monkeypatch.rctiming.resultsexport.ResultsOutboxItem;
import dev.monkeypatch.rctiming.resultsexport.ResultsPublishSettings;
import dev.monkeypatch.rctiming.domain.club.ResultsPublishTarget;
import dev.monkeypatch.rctiming.resultsexport.ResultsSiteService;
import dev.monkeypatch.rctiming.modules.RequiresModule;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportModule;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Results sent to RaceHub (#27): what is queued and sent, the publish target, retry now, and an event's results file. */
@RestController
@RequestMapping("/api/v1/admin")
@AdminOnly
@RequiresModule(ResultsExportModule.ID)
public class ResultsExportController {

    private static final int OUTBOX_ROWS = 100;

    private final ResultsExportService exportService;
    private final ResultsOutboxQuery outboxQuery;
    private final ClubProfileService clubProfileService;
    private final ResultsPublishSettings publishSettings;
    private final ResultsSiteService siteService;

    public ResultsExportController(ResultsExportService exportService, ResultsOutboxQuery outboxQuery,
                                   ClubProfileService clubProfileService,
                                   ResultsPublishSettings publishSettings, ResultsSiteService siteService) {
        this.exportService = exportService;
        this.outboxQuery = outboxQuery;
        this.clubProfileService = clubProfileService;
        this.publishSettings = publishSettings;
        this.siteService = siteService;
    }

    @GetMapping("/results-exports")
    public ResultsExportsDto list() {
        return new ResultsExportsDto(publishSettings.effective(),
                publishSettings.chosen(),
                outboxQuery.latest(OUTBOX_ROWS));
    }

    @Audited("audit_log")
    @PutMapping("/results-exports/target")
    public ResultsExportsDto setTarget(@RequestBody PublishTargetRequest request) {
        clubProfileService.updateResultsPublishTarget(CurrentOfficial.actor(),
                request.target() == null ? null : request.target().name());
        return list();
    }

    @Audited("audit_log")
    @PostMapping("/results-exports/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable long id) {
        exportService.retryNow(CurrentOfficial.actor(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Queues the event's results for RaceHub again. {@code 202} with the revision queued; 404 for an unknown event,
     * 409 when the event is not from RaceHub or the club does not publish there. Audited. Repeating it is safe.
     */
    @Audited("audit_log")
    @PostMapping("/events/{eventId}/results-export/reupload")
    public ResponseEntity<ReuploadResponse> reupload(@PathVariable long eventId) {
        ResultsOutboxItem item = exportService.reupload(CurrentOfficial.actor(), eventId);
        return ResponseEntity.accepted().body(new ReuploadResponse(item.getId(), item.getRevision()));
    }

    /**
     * The results site (#359) as a zip of static pages, built now from every event's results, for the club to upload
     * to its own web space. Available whatever the publish choice is.
     */
    @GetMapping("/results-site.zip")
    public ResponseEntity<byte[]> downloadSite() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("results-site.zip").build().toString())
                .body(siteService.zip());
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
