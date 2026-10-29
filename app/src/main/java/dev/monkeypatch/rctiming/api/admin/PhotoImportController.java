package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.PhotoImportRequest;
import dev.monkeypatch.rctiming.api.admin.dto.PhotoImportResponse;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.media.PhotoImportService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fetches the headshots and car photos an entry import named (#699), under
 * {@code /api/v1/admin/events/{eventId}}. ADMIN only, like the imports that produce the links. A link that cannot be
 * fetched is reported in the answer, not as an error, so one bad address never stops the rest.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}")
@AdminOnly
public class PhotoImportController {

    private final PhotoImportService service;

    public PhotoImportController(PhotoImportService service) {
        this.service = service;
    }

    /**
     * {@code POST /photo-import} with the links the import answered with. Always 200 with an outcome per link; 400 for
     * more than 1000 links; 404 for an unknown event. Audited, and each stored picture writes its own audit row.
     */
    @Audited("audit_log")
    @PostMapping("/photo-import")
    public PhotoImportResponse fetch(@PathVariable long eventId, @RequestBody @Valid PhotoImportRequest request) {
        return PhotoImportResponse.from(service.fetchAll(CurrentOfficial.actor(), eventId,
                request.links().stream().map(PhotoImportRequest.Link::toLink).toList(), request.replaceExisting()));
    }
}
