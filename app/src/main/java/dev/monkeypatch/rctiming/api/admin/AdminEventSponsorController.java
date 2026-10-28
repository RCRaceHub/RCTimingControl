package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.boards.dto.SponsorDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.sponsor.EventSponsorService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
 * An event's sponsors under {@code /api/v1/admin/events/{eventId}/sponsors} (#518). Reads are open to ADMIN,
 * RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad name or logo answers 400 and a
 * missing event or sponsor 404.
 */
@RestController
@RequestMapping("/api/v1/admin/events/{eventId}/sponsors")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class AdminEventSponsorController {

    private final EventSponsorService service;

    public AdminEventSponsorController(EventSponsorService service) {
        this.service = service;
    }

    /** The body of a create or rename. */
    public record SponsorRequest(@NotBlank @Size(max = 255) String name) {}

    /** The body of a reorder: the sponsor ids, first shown first. */
    public record SponsorOrderRequest(@NotNull List<Long> ids) {}

    /** The event's sponsors in display order. {@code GET}; read only. */
    @GetMapping
    public List<SponsorDto> list(@PathVariable long eventId) {
        return service.list(eventId).stream().map(SponsorDto::from).toList();
    }

    /** Adds a sponsor at the end. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400. Audited. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public SponsorDto create(Authentication auth, @PathVariable long eventId, @RequestBody @Valid SponsorRequest request) {
        return SponsorDto.from(service.create(CurrentOfficial.actor(auth), eventId, request.name()));
    }

    /** Renames a sponsor. {@code PUT /{id}}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public SponsorDto rename(Authentication auth, @PathVariable long eventId, @PathVariable long id,
                             @RequestBody @Valid SponsorRequest request) {
        return SponsorDto.from(service.rename(CurrentOfficial.actor(auth), eventId, id, request.name()));
    }

    /** Sets the display order. {@code PUT /order}; ADMIN or RACE_DIRECTOR; 404 for an id of another event. Audited. */
    @Audited("audit_log")
    @PutMapping("/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public List<SponsorDto> reorder(Authentication auth, @PathVariable long eventId, @RequestBody @Valid SponsorOrderRequest request) {
        return service.reorder(CurrentOfficial.actor(auth), eventId, request.ids() == null ? List.of() : request.ids())
                .stream().map(SponsorDto::from).toList();
    }

    /** Removes a sponsor. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable long eventId, @PathVariable long id) {
        service.delete(CurrentOfficial.actor(auth), eventId, id);
    }

    /**
     * Replaces a sponsor's logo. {@code PUT /{id}/logo} as multipart form data with a {@code file} part; ADMIN or
     * RACE_DIRECTOR; 400 for a missing, oversized or unsupported file; 404. Audited.
     */
    @Audited("audit_log")
    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public SponsorDto uploadLogo(Authentication auth, @PathVariable long eventId, @PathVariable long id,
                                 @RequestPart("file") MultipartFile file) {
        return SponsorDto.from(service.uploadLogo(CurrentOfficial.actor(auth), eventId, id, file));
    }
}
