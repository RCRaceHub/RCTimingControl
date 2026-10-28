package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.BoardViewRequest;
import dev.monkeypatch.rctiming.api.boards.dto.BoardViewDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.boardview.BoardView;
import dev.monkeypatch.rctiming.domain.boardview.BoardViewService;
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
 * Saved board views under {@code /api/v1/admin/board-views} (#481): list, read, create, change and delete. Reads are
 * open to ADMIN, RACE_DIRECTOR and REFEREE; every change is ADMIN or RACE_DIRECTOR and audited. A bad address name or
 * layout answers 400, an address name already taken 409 and a missing view 404.
 */
@RestController
@RequestMapping("/api/v1/admin/board-views")
@RaceControlStaff
public class AdminBoardViewController {

    private final BoardViewService service;

    public AdminBoardViewController(BoardViewService service) {
        this.service = service;
    }

    /** Every view. {@code GET}; read only. */
    @GetMapping
    public List<BoardViewDto> list() {
        return service.list().stream().map(this::dto).toList();
    }

    /** One view. {@code GET /{id}}; 404 if it does not exist. */
    @GetMapping("/{id}")
    public BoardViewDto get(@PathVariable Long id) {
        return dto(service.get(id));
    }

    /** Creates a view. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400; 409. Audited. */
    @Audited("audit_log")
    @PostMapping
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.CREATED)
    public BoardViewDto create(@RequestBody @Valid BoardViewRequest request) {
        return dto(service.create(CurrentOfficial.actor(), request.slug(), request.name(), request.columns(),
                request.panels()));
    }

    /** Changes a view. {@code PUT /{id}}; ADMIN or RACE_DIRECTOR; 400; 404; 409. Audited. */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @DirectorOrAdmin
    public BoardViewDto update(@PathVariable Long id, @RequestBody @Valid BoardViewRequest request) {
        return dto(service.update(CurrentOfficial.actor(), id, request.slug(), request.name(), request.columns(),
                request.panels()));
    }

    /** Deletes a view. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentOfficial.actor(), id);
    }

    private BoardViewDto dto(BoardView view) {
        return BoardViewDto.from(view, service.panelsOf(view));
    }
}
