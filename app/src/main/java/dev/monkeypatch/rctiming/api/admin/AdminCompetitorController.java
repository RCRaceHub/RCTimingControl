package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorAnonymiseService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorMergeService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorCategoryService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorNameVisibilityService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import dev.monkeypatch.rctiming.domain.competitor.NameVisibility;
import dev.monkeypatch.rctiming.infrastructure.tts.PiperTtsClient;
import dev.monkeypatch.rctiming.infrastructure.tts.TtsUnavailableException;
import dev.monkeypatch.rctiming.query.competitor.CompetitorChangeDto;
import dev.monkeypatch.rctiming.query.competitor.CompetitorDataExportService;
import dev.monkeypatch.rctiming.query.competitor.CompetitorDuplicateGroupDto;
import dev.monkeypatch.rctiming.query.competitor.CompetitorQueryService;
import dev.monkeypatch.rctiming.query.competitor.CompetitorSummaryDto;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Competitors under {@code /api/v1/admin/competitors}: the list, duplicate review and merge, how a name is said and
 * shown, and the privacy tools (a person's data export and anonymising). Open to ADMIN, RACE_DIRECTOR and REFEREE;
 * the review, merge and privacy endpoints are narrowed to ADMIN, and the name visibility to ADMIN or RACE_DIRECTOR.
 * Competitors have no login: they come from the RaceHub import or walk-ins. Changes are recorded in the audit log.
 */
@RestController
@RequestMapping("/api/v1/admin/competitors")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class AdminCompetitorController {

    private final CompetitorQueryService competitorQueryService;
    private final CompetitorService competitorService;
    private final CompetitorMergeService mergeService;
    private final CompetitorNameVisibilityService nameVisibilityService;
    private final CompetitorCategoryService categoryService;
    private final CompetitorAnonymiseService anonymiseService;
    private final CompetitorDataExportService exportService;
    private final ClubProfileService clubProfileService;
    private final PiperTtsClient piperClient;

    public AdminCompetitorController(CompetitorQueryService competitorQueryService,
                                     CompetitorService competitorService,
                                     CompetitorMergeService mergeService,
                                     CompetitorNameVisibilityService nameVisibilityService,
                                     CompetitorCategoryService categoryService,
                                     CompetitorAnonymiseService anonymiseService,
                                     CompetitorDataExportService exportService,
                                     ClubProfileService clubProfileService,
                                     PiperTtsClient piperClient) {
        this.competitorQueryService = competitorQueryService;
        this.competitorService = competitorService;
        this.mergeService = mergeService;
        this.nameVisibilityService = nameVisibilityService;
        this.categoryService = categoryService;
        this.anonymiseService = anonymiseService;
        this.exportService = exportService;
        this.clubProfileService = clubProfileService;
        this.piperClient = piperClient;
    }

    /** Every competitor with how their name is said and shown. {@code GET}; read only. */
    @GetMapping
    public List<CompetitorSummaryDto> listCompetitors() {
        return competitorQueryService.listAll();
    }

    /**
     * Competitors that may be one person entered twice, for an admin to review (#123).
     * {@code GET /possible-duplicates}; ADMIN.
     */
    @GetMapping("/possible-duplicates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CompetitorDuplicateGroupDto> possibleDuplicates() {
        return competitorQueryService.listPossibleDuplicates();
    }

    /**
     * What merging {@code duplicateId} into {@code keepId} would move, and whether it can go ahead (#123).
     * {@code GET /merge-preview}; ADMIN. Changes nothing; the preview lists any {@code blockers}.
     */
    @GetMapping("/merge-preview")
    @PreAuthorize("hasRole('ADMIN')")
    public CompetitorMergeService.Preview mergePreview(@RequestParam Long keepId, @RequestParam Long duplicateId) {
        return mergeService.preview(keepId, duplicateId);
    }

    /** The body of {@code POST /merge}: the competitor to keep and the duplicate to fold into it. */
    public record MergeRequest(@NotNull Long keepId, @NotNull Long duplicateId) {}

    /**
     * Moves the duplicate's entries onto the kept competitor and deletes the duplicate, in one
     * transaction (#123). 409 with the reasons when they can't be merged. {@code POST /merge}; ADMIN. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/merge")
    @PreAuthorize("hasRole('ADMIN')")
    public CompetitorMergeService.Result merge(Authentication auth, @Valid @RequestBody MergeRequest body) {
        return mergeService.merge(body.keepId(), body.duplicateId(), CurrentOfficial.id(auth));
    }

    /**
     * Everything held about one competitor, as a JSON download, for a request to see a person's data (#263). Admin
     * only; each export is recorded in the audit log. {@code GET /{id}/personal-data}, sent as an attachment that
     * browsers must not cache.
     */
    @GetMapping("/{id}/personal-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> personalData(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"competitor-" + id + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exportService.export(id, CurrentOfficial.id(auth)));
    }

    /**
     * Removes the competitor's name and identifiers everywhere RCTC keeps them, keeping results and standings (#263).
     * Cannot be undone. 409 while the competitor is in a race that is on the grid, running or stopped.
     * {@code POST /{id}/anonymise}; ADMIN. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/anonymise")
    @PreAuthorize("hasRole('ADMIN')")
    public CompetitorAnonymiseService.Result anonymise(Authentication auth, @PathVariable Long id) {
        return anonymiseService.anonymise(id, CurrentOfficial.id(auth));
    }

    /** Who changed how a competitor's name is said aloud, and when. {@code GET /{id}/changes}; ADMIN. */
    @GetMapping("/{id}/changes")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CompetitorChangeDto> changes(@PathVariable Long id) {
        return competitorQueryService.listChanges(id);
    }

    /** The body of {@code PUT /{id}/spoken-name}: how the name is said aloud. Null or blank clears it. */
    public record SpokenNameRequest(String spokenName) {}

    /** The body of {@code POST /spoken-name/preview}: the text to hear in the club's voice, required. */
    public record SpeechPreviewRequest(
            @NotBlank @Size(max = CompetitorService.MAX_SPOKEN_NAME_LENGTH) String text) {}

    /**
     * Set, change or clear how a competitor's name is said aloud (#119). Any official may, since it is
     * often noticed on the day; each change is recorded with who made it. {@code PUT /{id}/spoken-name}; ADMIN,
     * RACE_DIRECTOR or REFEREE. Answers with the competitor as saved.
     */
    @Audited("competitor_audit_log")
    @PutMapping("/{id}/spoken-name")
    public CompetitorSummaryDto setSpokenName(Authentication auth, @PathVariable Long id,
                                              @RequestBody SpokenNameRequest body) {
        Competitor c = competitorService.setSpokenName(id, body.spokenName(), CurrentOfficial.id(auth));
        return new CompetitorSummaryDto(c.getId(), c.getDisplayName(), c.getBrcaNumber(), c.getHomeClub(),
                c.getSpokenName(), c.speechName(), c.getNameVisibility().name(), c.categoryTags());
    }

    /** The body of {@code PUT /{id}/name-visibility}: how the name is shown on public screens. */
    public record NameVisibilityRequest(@NotNull NameVisibility nameVisibility) {}

    /**
     * Shows the competitor's full name, initials only, or no name on every public screen and in the live feed
     * (#265). Race directors and admins; officials' own screens keep the full name.
     * {@code PUT /{id}/name-visibility}. Answers with the competitor as saved. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/{id}/name-visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public CompetitorSummaryDto setNameVisibility(Authentication auth, @PathVariable Long id,
                                                  @Valid @RequestBody NameVisibilityRequest body) {
        Competitor c = nameVisibilityService.set(id, body.nameVisibility(), CurrentOfficial.id(auth));
        return new CompetitorSummaryDto(c.getId(), c.getDisplayName(), c.getBrcaNumber(), c.getHomeClub(),
                c.getSpokenName(), c.speechName(), c.getNameVisibility().name(), c.categoryTags());
    }

    /** The body of {@code PUT /{id}/categories}: the tags the driver holds; an empty list clears them. */
    public record CategoriesRequest(@NotNull List<@Size(max = 30) String> categories) {}

    /**
     * Sets the category tags a competitor holds, such as Junior, Vet or F-Grade, which a championship can split
     * its standings by (#494). Race directors and admins. {@code PUT /{id}/categories}. 400 for more than 8 tags,
     * a tag over 30 characters or one with a comma. Answers with the competitor as saved. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/{id}/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public CompetitorSummaryDto setCategories(Authentication auth, @PathVariable Long id,
                                              @Valid @RequestBody CategoriesRequest body) {
        Competitor c = categoryService.set(id, body.categories(), CurrentOfficial.id(auth));
        return new CompetitorSummaryDto(c.getId(), c.getDisplayName(), c.getBrcaNumber(), c.getHomeClub(),
                c.getSpokenName(), c.speechName(), c.getNameVisibility().name(), c.categoryTags());
    }

    /**
     * Speaks the text with the club's current Piper voice so an admin can hear a spoken name before saving
     * it (#119). 503 when Piper is not reachable; the page then uses the browser voice.
     * {@code POST /spoken-name/preview}, answered as {@code audio/wav}; any of the three roles. Changes nothing.
     */
    @PostMapping("/spoken-name/preview")
    public ResponseEntity<byte[]> previewSpokenName(@Valid @RequestBody SpeechPreviewRequest body) {
        try {
            // No club voice: null leaves it to Piper's configured default
            byte[] wav = piperClient.synthesize(body.text().trim(), clubProfileService.defaultVoiceId().orElse(null));
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(wav);
        } catch (TtsUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The announcer voice is not available");
        }
    }
}
