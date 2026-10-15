package dev.monkeypatch.rctiming.api.admin;

import dev.monkeypatch.rctiming.api.admin.dto.AddChampionshipClassRequest;
import dev.monkeypatch.rctiming.api.admin.dto.AddChampionshipEventRequest;
import dev.monkeypatch.rctiming.api.admin.dto.ChampionshipClassDto;
import dev.monkeypatch.rctiming.api.admin.dto.ChampionshipDetailDto;
import dev.monkeypatch.rctiming.api.admin.dto.ChampionshipDto;
import dev.monkeypatch.rctiming.api.admin.dto.ChampionshipEventLinkDto;
import dev.monkeypatch.rctiming.api.admin.dto.ChampionshipExclusionDto;
import dev.monkeypatch.rctiming.api.admin.dto.CreateChampionshipRequest;
import dev.monkeypatch.rctiming.api.admin.dto.CreateExclusionRequest;
import dev.monkeypatch.rctiming.api.admin.dto.PointsScaleEntryDto;
import dev.monkeypatch.rctiming.api.admin.dto.UpdateChampionshipRequest;
import dev.monkeypatch.rctiming.api.admin.dto.UpdatePointsScaleRequest;
import dev.monkeypatch.rctiming.domain.audit.Audited;
import dev.monkeypatch.rctiming.domain.championship.ChampionshipService;
import dev.monkeypatch.rctiming.query.championship.ChampionshipStandingsQuery;
import dev.monkeypatch.rctiming.query.championship.StandingsRowDto;
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

@RestController
@RequestMapping("/api/v1/admin/championships")
@PreAuthorize("hasAnyRole('ADMIN', 'RACE_DIRECTOR', 'REFEREE')")
public class ChampionshipController {

    private final ChampionshipService championshipService;
    private final ChampionshipStandingsQuery standingsQuery;

    public ChampionshipController(ChampionshipService championshipService,
                                   ChampionshipStandingsQuery standingsQuery) {
        this.championshipService = championshipService;
        this.standingsQuery = standingsQuery;
    }

    @GetMapping
    public List<ChampionshipDto> list() {
        return championshipService.listAll().stream().map(ChampionshipDto::from).toList();
    }

    @GetMapping("/{id}")
    public ChampionshipDetailDto getDetail(@PathVariable Long id) {
        ChampionshipService.Detail detail = championshipService.getDetail(id);
        return ChampionshipDetailDto.from(detail.championship(),
                detail.classes().stream().map(ChampionshipClassDto::from).toList(),
                detail.events().stream().map(ChampionshipEventLinkDto::from).toList(),
                detail.pointsScale().stream().map(PointsScaleEntryDto::from).toList());
    }

    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipDto create(Authentication auth, @RequestBody @Valid CreateChampionshipRequest request) {
        return ChampionshipDto.from(championshipService.create(CurrentOfficial.actor(auth), settings(request)));
    }

    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ChampionshipDto update(Authentication auth, @PathVariable Long id,
                                   @RequestBody @Valid UpdateChampionshipRequest request) {
        return ChampionshipDto.from(championshipService.update(CurrentOfficial.actor(auth), id, settings(request)));
    }

    @Audited("audit_log")
    @PostMapping("/{id}/classes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipClassDto addClass(Authentication auth, @PathVariable Long id,
                                          @RequestBody @Valid AddChampionshipClassRequest request) {
        return ChampionshipClassDto.from(championshipService.addClass(CurrentOfficial.actor(auth), id,
                request.racingClassId(), request.bestXFromYX(), request.bestXFromYY()));
    }

    @Audited("audit_log")
    @DeleteMapping("/{id}/classes/{racingClassId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeClass(Authentication auth, @PathVariable Long id, @PathVariable Long racingClassId) {
        championshipService.removeClass(CurrentOfficial.actor(auth), id, racingClassId);
    }

    @Audited("audit_log")
    @PostMapping("/{id}/events")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipEventLinkDto linkEvent(Authentication auth, @PathVariable Long id,
                                               @RequestBody @Valid AddChampionshipEventRequest request) {
        return ChampionshipEventLinkDto.from(championshipService.linkEvent(CurrentOfficial.actor(auth), id,
                request.eventId(), request.roundNumber()));
    }

    @Audited("audit_log")
    @DeleteMapping("/{id}/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkEvent(Authentication auth, @PathVariable Long id, @PathVariable Long eventId) {
        championshipService.unlinkEvent(CurrentOfficial.actor(auth), id, eventId);
    }

    @Audited("audit_log")
    @PutMapping("/{id}/points-scale")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PointsScaleEntryDto> replacePointsScale(Authentication auth, @PathVariable Long id,
                                                         @RequestBody @Valid UpdatePointsScaleRequest request) {
        List<ChampionshipService.ScalePoint> scale = request.entries().stream()
                .map(e -> new ChampionshipService.ScalePoint(e.position(), e.points()))
                .toList();
        return championshipService.replacePointsScale(CurrentOfficial.actor(auth), id, scale).stream()
                .map(PointsScaleEntryDto::from)
                .toList();
    }

    @GetMapping("/{id}/exclusions")
    public List<ChampionshipExclusionDto> listExclusions(@PathVariable Long id) {
        return championshipService.listExclusions(id).stream().map(ChampionshipExclusionDto::from).toList();
    }

    // Not admin-only, unlike the rest of championship setup: a referee records a disqualification (DQ), so
    // every official may add or remove an exclusion (#132). The flow is to be revisited after user testing.
    @Audited("audit_log")
    @PostMapping("/{id}/exclusions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipExclusionDto createExclusion(@PathVariable Long id,
                                                     Authentication auth,
                                                     @RequestBody @Valid CreateExclusionRequest request) {
        return ChampionshipExclusionDto.from(championshipService.createExclusion(CurrentOfficial.actor(auth), id,
                request.driverId(), request.eventId(), request.reason()));
    }

    @Audited("audit_log")
    @DeleteMapping("/{id}/exclusions/{exclusionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExclusion(Authentication auth, @PathVariable Long id, @PathVariable Long exclusionId) {
        championshipService.deleteExclusion(CurrentOfficial.actor(auth), id, exclusionId);
    }

    /** The championship's standings, worked out from the result snapshots each time. */
    @GetMapping("/{id}/standings")
    public List<StandingsRowDto> getStandings(@PathVariable Long id) {
        return standingsQuery.computeStandings(id);
    }

    private static ChampionshipService.Settings settings(CreateChampionshipRequest r) {
        return new ChampionshipService.Settings(r.name(), r.bestXFromYX(), r.bestXFromYY(), r.scoringSource(),
                r.tqBonusPoints(), r.afinalWinnerBonusPoints());
    }

    private static ChampionshipService.Settings settings(UpdateChampionshipRequest r) {
        return new ChampionshipService.Settings(r.name(), r.bestXFromYX(), r.bestXFromYY(), r.scoringSource(),
                r.tqBonusPoints(), r.afinalWinnerBonusPoints());
    }
}
