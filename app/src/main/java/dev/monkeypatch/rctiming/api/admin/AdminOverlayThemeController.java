package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.OverlayThemeRequest;
import dev.monkeypatch.rctiming.api.boards.dto.OverlayThemeDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayTheme;
import dev.monkeypatch.rctiming.domain.overlaytheme.OverlayThemeService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saved overlay themes under {@code /api/v1/admin/overlay-themes}: list, read, create, change and delete. Reads are
 * open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad name or design
 * answers 400, a name already taken 409 and a missing theme 404.
 */
@RestController
@RequestMapping("/api/v1/admin/overlay-themes")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class AdminOverlayThemeController {

    private final OverlayThemeService service;

    public AdminOverlayThemeController(OverlayThemeService service) {
        this.service = service;
    }

    /** Every theme, by name. {@code GET}; read only. */
    @GetMapping
    public List<OverlayThemeDto> list() {
        return service.list().stream().map(this::dto).toList();
    }

    /** Creates a theme. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400; 409. Audited. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public OverlayThemeDto create(Authentication auth, @RequestBody @Valid OverlayThemeRequest request) {
        return dto(service.create(CurrentOfficial.actor(auth), request.name(), request.design()));
    }

    /** Changes a theme. {@code PUT /{id}}; ADMIN or RACE_DIRECTOR; 400; 404; 409. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    public OverlayThemeDto update(Authentication auth, @PathVariable Long id, @RequestBody @Valid OverlayThemeRequest request) {
        return dto(service.update(CurrentOfficial.actor(auth), id, request.name(), request.design()));
    }

    /** Deletes a theme. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        service.delete(CurrentOfficial.actor(auth), id);
    }

    private OverlayThemeDto dto(OverlayTheme theme) {
        return OverlayThemeDto.from(theme, service.designOf(theme));
    }
}
