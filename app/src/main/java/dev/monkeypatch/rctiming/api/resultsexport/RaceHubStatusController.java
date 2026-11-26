package dev.monkeypatch.rctiming.api.resultsexport;

import dev.monkeypatch.rctiming.modules.RequiresModule;
import dev.monkeypatch.rctiming.resultsexport.ResultsExportModule;
import dev.monkeypatch.rctiming.api.resultsexport.dto.RaceHubStatusDto;
import dev.monkeypatch.rctiming.resultsexport.RaceHubConnectionService;
import dev.monkeypatch.rctiming.security.RaceControlStaff;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the link to RaceHub is alive. {@code GET /api/v1/race-control/racehub/status}; read only, for the
 * RACE_DIRECTOR, REFEREE and ADMIN roles. It answers from the background check's last result and the results outbox,
 * so it never waits on RaceHub and is cheap to poll.
 */
@RestController
@RequestMapping("/api/v1/race-control/racehub")
@RaceControlStaff
@RequiresModule(ResultsExportModule.ID)
public class RaceHubStatusController {

    private final RaceHubConnectionService service;

    public RaceHubStatusController(RaceHubConnectionService service) {
        this.service = service;
    }

    /** The link's health and how results sending stands; {@code NOT_CONFIGURED} for a standalone club. */
    @GetMapping("/status")
    public RaceHubStatusDto status() {
        return RaceHubStatusDto.from(service.status());
    }
}
