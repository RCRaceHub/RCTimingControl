package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.RaceHubConnectionTestDto;
import dev.monkeypatch.rctiming.resultsexport.RaceHubConnectionService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Test connection" for the RaceHub page. {@code POST /api/v1/admin/racehub/test}; ADMIN only. It changes nothing
 * in RCTC or RaceHub, so it is not audited; it blocks for RaceHub's answer (up to about 30 seconds if RaceHub is
 * slow), so the page shows a spinner.
 */
@RestController
@RequestMapping("/api/v1/admin/racehub/test")
@AdminOnly
public class RaceHubConnectionController {

    private final RaceHubConnectionService service;

    public RaceHubConnectionController(RaceHubConnectionService service) {
        this.service = service;
    }

    /** Runs the checks with the address and key now in use and answers each one's result; always a 200. */
    @PostMapping
    public RaceHubConnectionTestDto test() {
        return RaceHubConnectionTestDto.from(service.test());
    }
}
