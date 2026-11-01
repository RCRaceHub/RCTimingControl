package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.ScreenPlaylistDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenPlaylistRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.screens.ScreenPlaylist;
import dev.monkeypatch.rctiming.screens.ScreenService;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import dev.monkeypatch.rctiming.security.DirectorOrAdmin;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * The saved screen playlists under {@code /api/v1/admin/screen-playlists} (#708): list, create, change and delete.
 * Reads are open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad name,
 * time or page answers 400, a name already taken 409 and a missing playlist 404. Changing a playlist does not touch
 * the screens; it takes effect when it is pushed.
 */
@RestController
@RequestMapping("/api/v1/admin/screen-playlists")
@RaceControlStaff
public class AdminScreenPlaylistController {

    private final ScreenService service;

    public AdminScreenPlaylistController(ScreenService service) {
        this.service = service;
    }

    /** Every playlist. {@code GET}; read only. */
    @GetMapping
    public List<ScreenPlaylistDto> list() {
        return service.playlists().stream().map(this::dto).toList();
    }

    /** Creates a playlist. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400; 409. Audited. */
    @Audited("audit_log")
    @PostMapping
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenPlaylistDto create(@RequestBody @Valid ScreenPlaylistRequest request) {
        return dto(service.createPlaylist(CurrentOfficial.actor(), request.name(), request.defaultDuration(), request.items()));
    }

    /** Changes a playlist. {@code PUT /{id}}; ADMIN or RACE_DIRECTOR; 400; 404; 409. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @DirectorOrAdmin
    public ScreenPlaylistDto update(@PathVariable Long id, @RequestBody @Valid ScreenPlaylistRequest request) {
        return dto(service.updatePlaylist(CurrentOfficial.actor(), id, request.name(), request.defaultDuration(), request.items()));
    }

    /** Deletes a playlist. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.deletePlaylist(CurrentOfficial.actor(), id);
    }

    private ScreenPlaylistDto dto(ScreenPlaylist playlist) {
        return ScreenPlaylistDto.from(playlist, service.itemsOf(playlist));
    }
}
