package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.AddScreenRequest;
import dev.monkeypatch.rctiming.api.admin.dto.BoardPagesDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenActionResultDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenControlRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenDto;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenIdsRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenPushRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenShowRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreenTokenRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ScreensDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.screens.BoardPageCatalog;
import dev.monkeypatch.rctiming.screens.ControlAction;
import dev.monkeypatch.rctiming.screens.Screen;
import dev.monkeypatch.rctiming.screens.ScreenActionResult;
import dev.monkeypatch.rctiming.screens.ScreenControlService;
import dev.monkeypatch.rctiming.screens.ScreenDiscovery;
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
 * The screen manager under {@code /api/v1/admin/screens} (#708): the EventScreenDisplay viewers on the venue network,
 * their live status, and commands to one or several of them. Reads are open to ADMIN, RACE_DIRECTOR and REFEREE; every
 * change or command is ADMIN or RACE_DIRECTOR and audited. A screen that cannot be reached when it is added answers
 * 502; a command to a group answers 200 with one result per screen, failures included.
 */
@RestController
@RequestMapping("/api/v1/admin/screens")
@RaceControlStaff
public class AdminScreenController {

    private final ScreenControlService control;
    private final ScreenService screens;
    private final ScreenDiscovery discovery;
    private final BoardPageCatalog boardPages;

    public AdminScreenController(ScreenControlService control, ScreenService screens, ScreenDiscovery discovery,
                                 BoardPageCatalog boardPages) {
        this.control = control;
        this.screens = screens;
        this.discovery = discovery;
        this.boardPages = boardPages;
    }

    /** Every known screen with its live state; polled by the page every few seconds. {@code GET}; read only. */
    @GetMapping
    public ScreensDto list() {
        return new ScreensDto(discovery.enabled(), control.overview().stream().map(ScreenDto::from).toList());
    }

    /** The app's own pages to put on a screen, under the addresses the screens can reach. {@code GET /board-pages}. */
    @GetMapping("/board-pages")
    public BoardPagesDto boardPages() {
        return BoardPagesDto.from(boardPages.choices());
    }

    /** Adds a screen by host and port. {@code POST}; ADMIN or RACE_DIRECTOR; 201; 400; 502 if it cannot be reached. Audited. */
    @Audited("audit_log")
    @PostMapping
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenDto add(@RequestBody @Valid AddScreenRequest request) {
        Screen screen = control.add(CurrentOfficial.actor(), request.host(),
                request.port() == null ? 8080 : request.port(), request.token());
        return ScreenDto.from(control.overviewOf(screen));
    }

    /** Asks the network for screens now instead of waiting for the next pass. {@code POST /scan}; ADMIN or RACE_DIRECTOR; 204. Audited. */
    @Audited("audit_log")
    @PostMapping("/scan")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void scan() {
        discovery.scanNow();
        screens.auditAction(CurrentOfficial.actor(), "SCREENS_SCANNED", "Looked for display screens on the network");
    }

    /** Forgets a screen. {@code DELETE /{id}}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        screens.remove(CurrentOfficial.actor(), id);
    }

    /** Sets (or, blank, clears) one token on the screens listed. {@code PUT /token}; ADMIN or RACE_DIRECTOR; 204; 404. Audited. */
    @Audited("audit_log")
    @PutMapping("/token")
    @DirectorOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setToken(@RequestBody @Valid ScreenTokenRequest request) {
        screens.setToken(CurrentOfficial.actor(), request.screenIds(), request.token());
    }

    /** Pushes a saved playlist to screens. {@code POST /push}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/push")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> push(@RequestBody @Valid ScreenPushRequest request) {
        return results(control.push(CurrentOfficial.actor(), request.playlistId(), request.screenIds()));
    }

    /** Pushes a screen's assigned playlist again, to undo a change made elsewhere. {@code POST /{id}/repush}; 400; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/{id}/repush")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> repush(@PathVariable Long id) {
        return results(control.repush(CurrentOfficial.actor(), id));
    }

    /**
     * Sends next, previous, goto, pause, resume or reload to screens. {@code POST /control/{action}}; ADMIN or
     * RACE_DIRECTOR; 400 for an unknown action; 404. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/control/{action}")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> sendControl(@PathVariable String action, @RequestBody @Valid ScreenControlRequest request) {
        return results(control.control(CurrentOfficial.actor(), request.screenIds(), ControlAction.parse(action),
                request.page() == null ? null : request.page() - 1));
    }

    /** Puts a temporary page on screens. {@code POST /show}; ADMIN or RACE_DIRECTOR; 400; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/show")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> show(@RequestBody @Valid ScreenShowRequest request) {
        return results(control.show(CurrentOfficial.actor(), request.screenIds(), request.url(), request.seconds(),
                request.label()));
    }

    /** Ends the temporary page on screens. {@code POST /show/clear}; ADMIN or RACE_DIRECTOR; 404. Audited. */
    @Audited("audit_log")
    @PostMapping("/show/clear")
    @DirectorOrAdmin
    public List<ScreenActionResultDto> clearShow(@RequestBody @Valid ScreenIdsRequest request) {
        return results(control.clearShow(CurrentOfficial.actor(), request.screenIds()));
    }

    private static List<ScreenActionResultDto> results(List<ScreenActionResult> results) {
        return results.stream().map(ScreenActionResultDto::from).toList();
    }
}
