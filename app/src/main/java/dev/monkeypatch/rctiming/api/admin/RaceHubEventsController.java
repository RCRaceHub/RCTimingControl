package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.EventDto;
import dev.monkeypatch.rctiming.api.admin.dto.RaceHubEventsDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEventsService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The "Find RaceHub events" picker: which of the club's events RaceHub has released for timing, and taking one. Under
 * {@code /api/v1/admin/racehub/events}; ADMIN only. RCTC asks RaceHub (it is never called by it), using the club's
 * key and the one RaceHub address, set on the RaceHub page (see {@code RaceHubAddressController}).
 */
@RestController
@RequestMapping("/api/v1/admin/racehub/events")
@AdminOnly
public class RaceHubEventsController {

    private final RaceHubEventsService service;

    public RaceHubEventsController(RaceHubEventsService service) {
        this.service = service;
    }

    /**
     * RaceHub's address and the events it offers now. A RaceHub that cannot be reached or refuses the key is
     * still a 200 with {@code error} set, so the page can show why. {@code state} is {@code ready} (released to
     * timing, the default), {@code open} (open for sign-up) or {@code all} (every upcoming event); {@code from} and
     * {@code to} (ISO dates, inclusive) narrow it by the event's date. 400 for a bad state or date, or when
     * {@code from} is after {@code to}.
     */
    @GetMapping
    public RaceHubEventsDto list(
            @RequestParam(defaultValue = "ready") String state,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return RaceHubEventsDto.of(service.settings(), service.list(RaceHubEventsService.View.parse(state), from, to));
    }

    /**
     * Takes an offered event: creates it in RCTC if needed and sets its entry feed to RaceHub's address with
     * auto-fetch on. {@code POST}; answers the RCTC event. A 404 if RaceHub no longer offers it, 409 if RaceHub
     * cannot be asked. {@code state} is the view the event was picked from (default {@code ready}). Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{raceHubEventId}/take")
    public EventDto take(@PathVariable String raceHubEventId,
                         @RequestParam(defaultValue = "ready") String state) {
        return EventDto.from(service.take(CurrentOfficial.actor(), raceHubEventId, RaceHubEventsService.View.parse(state)));
    }
}
