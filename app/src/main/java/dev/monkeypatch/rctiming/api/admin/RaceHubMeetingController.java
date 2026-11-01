package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.RaceHubMeetingDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubEntryExport;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubMeetingService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Makes the event for an Entry Export file so it can be imported without creating one first, under
 * {@code /api/v1/admin/racehub/meeting}. ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/admin/racehub/meeting")
@AdminOnly
public class RaceHubMeetingController {

    private final RaceHubMeetingService service;

    public RaceHubMeetingController(RaceHubMeetingService service) {
        this.service = service;
    }

    /**
     * Finds the event for the file (matched by RaceHub's event id), or creates it in DRAFT from the file's name and
     * date. {@code POST}; 400 for a file that is not a supported Entry Export or has no event date. Audited.
     */
    @Audited("audit_log")
    @PostMapping
    public RaceHubMeetingDto findOrCreate(@RequestBody RaceHubEntryExport export) {
        return RaceHubMeetingDto.from(service.findOrCreate(CurrentOfficial.actor(), export));
    }
}
