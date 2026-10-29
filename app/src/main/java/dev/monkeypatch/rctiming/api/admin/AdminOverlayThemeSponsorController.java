package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.SponsorOrderRequest;
import dev.monkeypatch.rctiming.api.admin.dto.SponsorRequest;
import dev.monkeypatch.rctiming.api.boards.dto.SponsorDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.sponsor.OverlayThemeSponsorService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * An overlay theme's sponsors under {@code /api/v1/admin/overlay-themes/{themeId}/sponsors} . Reads are open to ADMIN,
 * RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad name or logo answers 400 and a
 * missing theme or sponsor 404.
 */
@RestController
@RequestMapping("/api/v1/admin/overlay-themes/{themeId}/sponsors")
@RaceControlStaff
public class AdminOverlayThemeSponsorController {

    private final OverlayThemeSponsorService service;

    public AdminOverlayThemeSponsorController(OverlayThemeSponsorService service) {
        this.service = service;
    }

    /** The theme's sponsors in display order. {@code GET}; read only. */
    @GetMapping
    public List<SponsorDto> list(@PathVariable long themeId) {
        return service.list(themeId).stream().map(SponsorDto::from).toList();
    }

    /** Adds a sponsor at the end. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400. Audited. */
    @Audited("audit_log")
    @PostMapping
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.CREATED)
    public SponsorDto create(@PathVariable long themeId, @RequestBody @Valid SponsorRequest request) {
        return SponsorDto.from(service.create(CurrentOfficial.actor(), themeId, request.name()));
    }

    /** Renames a sponsor. {@code PUT /{id}}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @DirectorOrAdmin
    public SponsorDto rename(@PathVariable long themeId, @PathVariable long id,
                             @RequestBody @Valid SponsorRequest request) {
        return SponsorDto.from(service.rename(CurrentOfficial.actor(), themeId, id, request.name()));
    }

    /** Sets the display order. {@code PUT /order}; ADMIN or RACE_DIRECTOR; 404 for an id of another theme. Audited. */
    @Audited("audit_log")
    @PutMapping("/order")
    @DirectorOrAdmin
    public List<SponsorDto> reorder(@PathVariable long themeId, @RequestBody SponsorOrderRequest request) {
        return service.reorder(CurrentOfficial.actor(), themeId, request.ids() == null ? List.of() : request.ids())
                .stream().map(SponsorDto::from).toList();
    }

    /** Removes a sponsor. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long themeId, @PathVariable long id) {
        service.delete(CurrentOfficial.actor(), themeId, id);
    }

    /**
     * Replaces a sponsor's logo. {@code PUT /{id}/logo} as multipart form data with a {@code file} part; ADMIN or
     * RACE_DIRECTOR; 400 for a missing, oversized or unsupported file; 404. Audited.
     */
    @Audited("audit_log")
    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @DirectorOrAdmin
    public SponsorDto uploadLogo(@PathVariable long themeId, @PathVariable long id,
                                 @RequestPart("file") MultipartFile file) {
        return SponsorDto.from(service.uploadLogo(CurrentOfficial.actor(), themeId, id, file));
    }
}
