package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.LinkRaceHubClassRequest;
import dev.monkeypatch.rctiming.api.admin.dto.RaceHubClassesDto;
import dev.monkeypatch.rctiming.api.admin.dto.ResolvedRaceHubClassDto;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassLinkService;
import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassesService;
import dev.monkeypatch.rctiming.security.AdminOnly;
import dev.monkeypatch.rctiming.security.CurrentOfficial;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The "RaceHub classes" screen: the club's classes as RaceHub lists them, and linking each to a racing class once for
 * the whole club. Under {@code /api/v1/admin/racehub}; ADMIN only. RCTC asks RaceHub, using the club's key from the
 * settings file.
 */
@RestController
@RequestMapping("/api/v1/admin/racehub")
@AdminOnly
public class RaceHubClassesController {

    private final RaceHubClassesService classesService;
    private final RaceHubClassLinkService linkService;

    public RaceHubClassesController(RaceHubClassesService classesService, RaceHubClassLinkService linkService) {
        this.classesService = classesService;
        this.linkService = linkService;
    }

    /**
     * The club's classes now, inactive ones included, each with its link. A RaceHub that cannot be reached or
     * refuses the key is still a 200 with {@code error} set, so the page can show why. Calling it again re-fetches.
     */
    @GetMapping("/classes")
    public RaceHubClassesDto classes() {
        return RaceHubClassesDto.from(classesService.list());
    }

    /**
     * Links club classes to racing classes for the whole club, no event involved: each to an existing racing class or
     * a new one made from {@code createName}. 400 for a bad request, 404 for an unknown racing class. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/class-links")
    public List<ResolvedRaceHubClassDto> link(@RequestBody List<LinkRaceHubClassRequest> requests) {
        return linkService.link(CurrentOfficial.actor(), requests.stream()
                        .map(r -> new RaceHubClassLinkService.Resolution(r.clubClassId(), r.racingClassId(),
                                r.createName(), null)).toList())
                .stream().map(ResolvedRaceHubClassDto::from).toList();
    }
}
