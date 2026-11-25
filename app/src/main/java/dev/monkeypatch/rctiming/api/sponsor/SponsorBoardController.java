package dev.monkeypatch.rctiming.api.sponsor;

import dev.monkeypatch.rctiming.api.sponsor.dto.SponsorDto;
import dev.monkeypatch.rctiming.domain.sponsor.EventSponsorService;
import dev.monkeypatch.rctiming.domain.sponsor.OverlayThemeSponsorService;
import dev.monkeypatch.rctiming.domain.sponsor.SponsorsModule;
import dev.monkeypatch.rctiming.modules.ModuleSwitch;
import dev.monkeypatch.rctiming.modules.RequiresModule;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The sponsors for the overlay's sponsor bar (#518), anonymous like the other boards. Part of the sponsors module:
 * 404 while the club has it off, and an empty list for an event that turned it off.
 */
@RestController
@RequestMapping("/api/v1/boards")
@RequiresModule(SponsorsModule.ID)
public class SponsorBoardController {

    private final EventSponsorService eventSponsorService;
    private final OverlayThemeSponsorService overlayThemeSponsorService;
    private final BoardQuery boardQuery;
    private final ModuleSwitch moduleSwitch;

    public SponsorBoardController(EventSponsorService eventSponsorService,
                                  OverlayThemeSponsorService overlayThemeSponsorService,
                                  BoardQuery boardQuery, ModuleSwitch moduleSwitch) {
        this.eventSponsorService = eventSponsorService;
        this.overlayThemeSponsorService = overlayThemeSponsorService;
        this.boardQuery = boardQuery;
        this.moduleSwitch = moduleSwitch;
    }

    /**
     * The sponsors in display order, names and logos only. {@code GET /api/v1/boards/sponsors}. The overlay theme's
     * own sponsors when a {@code themeId} is given and that theme has any, otherwise the event's.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     * @param themeId the overlay theme the overlay was opened with ({@code ?look=ID}), if any; a theme that is gone
     *     or has no sponsors falls back to the event's
     */
    @GetMapping("/sponsors")
    public List<SponsorDto> sponsors(@RequestParam(required = false) Long eventId,
                                     @RequestParam(required = false) Long themeId) {
        var event = boardQuery.resolveEvent(eventId);
        if (event.isPresent() && !moduleSwitch.isEnabled(SponsorsModule.ID, event.get().id())) {
            return List.of();
        }
        if (themeId != null) {
            List<SponsorDto> own = overlayThemeSponsorService.listForOverlay(themeId).stream().map(SponsorDto::from).toList();
            if (!own.isEmpty()) {
                return own;
            }
        }
        return event.map(e -> eventSponsorService.list(e.id()).stream().map(SponsorDto::from).toList()).orElse(List.of());
    }
}
