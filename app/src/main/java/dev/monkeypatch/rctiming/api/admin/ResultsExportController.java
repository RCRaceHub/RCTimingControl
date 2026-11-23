package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.PublishTargetRequest;
import dev.monkeypatch.rctiming.api.admin.dto.RaceHubKeyRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ResultsUrlRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.query.resultsexport.ResultsOutboxQuery;
import dev.monkeypatch.rctiming.resultsexport.RaceHubKeyService;
import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportService;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportV1;
import dev.monkeypatch.rctiming.resultsexport.ResultsPublishSettings;
import dev.monkeypatch.rctiming.resultsexport.ResultsPublishTarget;
import dev.monkeypatch.rctiming.resultsexport.ResultsSiteService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@AdminOnly
public class ResultsExportController {

    private static final int OUTBOX_ROWS = 100;

    private final ResultsExportService exportService;
    private final ResultsOutboxQuery outboxQuery;
    private final RaceHubResultsEndpoint endpoint;
    private final ClubProfileService clubProfileService;
    private final ResultsPublishSettings publishSettings;
    private final ResultsSiteService siteService;
    private final RaceHubKeyService keyService;

    public ResultsExportController(ResultsExportService exportService, ResultsOutboxQuery outboxQuery,
                                   RaceHubResultsEndpoint endpoint, ClubProfileService clubProfileService,
                                   ResultsPublishSettings publishSettings, ResultsSiteService siteService,
                                   RaceHubKeyService keyService) {
        this.exportService = exportService;
        this.outboxQuery = outboxQuery;
        this.endpoint = endpoint;
        this.clubProfileService = clubProfileService;
        this.publishSettings = publishSettings;
        this.siteService = siteService;
        this.keyService = keyService;
    }

    /**
     * @param sendingEnabled  whether the RaceHub address and key are both set; without them, exports wait
     * @param resultsUrl      the address in use, or null
     * @param savedUrl        the address set here, or null when the settings file's is in use
     * @param missingSettings what is still needed before anything is sent
     * @param keySource       where the key comes from: {@code SAVED} (set here), {@code SETTINGS_FILE} or {@code NONE}
     * @param publishTarget   where results are published now: the club's choice, else the default
     * @param chosenTarget    the club's choice, or null when it has not made one
     * @param exports         the newest exports first
     */
    public record ResultsExportsDto(boolean sendingEnabled, String resultsUrl, String savedUrl,
                                    List<String> missingSettings, String keySource, ResultsPublishTarget publishTarget,
                                    ResultsPublishTarget chosenTarget,
                                    List<ResultsOutboxQuery.OutboxRow> exports) {}

    @GetMapping("/results-exports")
    public ResultsExportsDto list() {
        var settings = endpoint.snapshot();
        return new ResultsExportsDto(settings.sendingEnabled(),
                settings.resultsUrl() == null ? null : settings.resultsUrl().toString(),
                settings.savedUrl(),
                settings.missingSettings(),
                keySource(),
                publishSettings.effective(),
                publishSettings.chosen(),
                outboxQuery.latest(OUTBOX_ROWS));
    }

    @Audited("audit_log")
    @PutMapping("/results-exports/address")
    public ResultsExportsDto setAddress(@RequestBody @Valid ResultsUrlRequest request) {
        if (request.resultsUrl() != null && !request.resultsUrl().isBlank()) {
            RaceHubResultsEndpoint.parse(request.resultsUrl());
        }
        clubProfileService.updateRaceHubResultsUrl(CurrentOfficial.actor(), request.resultsUrl());
        return list();
    }

    /**
     * Saves a new RaceHub key, used by the next request with no restart, and gives it to the entry feeds that point
     * at RaceHub. {@code PUT}; the key is never returned. A 400 for a key with spaces. Audited without the key.
     */
    @Audited("audit_log")
    @PutMapping("/results-exports/key")
    public ResultsExportsDto setKey(@RequestBody @Valid RaceHubKeyRequest request) {
        keyService.save(CurrentOfficial.actor(), request.key());
        return list();
    }

    /** Forgets the saved key so the settings file's applies again. {@code DELETE}. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/results-exports/key")
    public ResultsExportsDto clearKey() {
        keyService.clear(CurrentOfficial.actor());
        return list();
    }

    private String keySource() {
        return endpoint.keySaved() ? "SAVED" : endpoint.settingsFileKeySet() ? "SETTINGS_FILE" : "NONE";
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
