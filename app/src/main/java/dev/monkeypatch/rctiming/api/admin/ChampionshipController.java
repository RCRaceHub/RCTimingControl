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

/**
 * Championship setup and standings under {@code /api/v1/admin/championships}: the championship's settings, its
 * classes, the events linked as rounds, the points scale and driver exclusions. Reads and the standings are open to
 * ADMIN, RACE_DIRECTOR and REFEREE; creating and changing a championship is ADMIN only. The exception is
 * exclusions, which any of the three may add or remove (#132). Changes are recorded in the audit log. Spectators
 * read standings from {@code PublicChampionshipController}. A missing championship, class, event or exclusion is a
 * 404. How points are scored is in {@code docs/scoring.md}.
 */
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

    /** Every championship. {@code GET}; read only. */
    @GetMapping
    public List<ChampionshipDto> list() {
        return championshipService.listAll().stream().map(ChampionshipDto::from).toList();
    }

    /**
     * One championship with its classes, its events in round order and its points scale.
     * {@code GET /{id}}; 404 if it does not exist.
     */
    @GetMapping("/{id}")
    public ChampionshipDetailDto getDetail(@PathVariable Long id) {
        ChampionshipService.Detail detail = championshipService.getDetail(id);
        return ChampionshipDetailDto.from(detail.championship(),
                detail.classes().stream().map(ChampionshipClassDto::from).toList(),
                detail.events().stream().map(ChampionshipEventLinkDto::from).toList(),
                detail.pointsScale().stream().map(PointsScaleEntryDto::from).toList());
    }

    /** Creates a championship. {@code POST}; ADMIN; answers 201; 400 for a bad body. Audited. */
    @Audited("audit_log")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipDto create(Authentication auth, @RequestBody @Valid CreateChampionshipRequest request) {
        return ChampionshipDto.from(championshipService.create(CurrentOfficial.actor(auth), settings(request)));
    }

    /**
     * Replaces the championship's own settings (name, best X from Y, scoring source and bonuses).
     * {@code PUT /{id}}; ADMIN. Audited with the settings before and after.
     */
    @Audited("audit_log")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ChampionshipDto update(Authentication auth, @PathVariable Long id,
                                   @RequestBody @Valid UpdateChampionshipRequest request) {
        return ChampionshipDto.from(championshipService.update(CurrentOfficial.actor(auth), id, settings(request)));
    }

    /**
     * Adds a racing class to the championship; a null best X from Y inherits the championship's.
     * {@code POST /{id}/classes}; ADMIN; answers 201. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/classes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipClassDto addClass(Authentication auth, @PathVariable Long id,
                                          @RequestBody @Valid AddChampionshipClassRequest request) {
        return ChampionshipClassDto.from(championshipService.addClass(CurrentOfficial.actor(auth), id,
                request.racingClassId(), request.bestXFromYX(), request.bestXFromYY()));
    }

    /**
     * Removes a racing class from the championship. {@code DELETE /{id}/classes/{racingClassId}}; ADMIN; 204.
     * Audited.
     */
    @Audited("audit_log")
    @DeleteMapping("/{id}/classes/{racingClassId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeClass(Authentication auth, @PathVariable Long id, @PathVariable Long racingClassId) {
        championshipService.removeClass(CurrentOfficial.actor(auth), id, racingClassId);
    }

    /**
     * Links an event to the championship as a numbered round. {@code POST /{id}/events}; ADMIN; answers 201.
     * Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/events")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipEventLinkDto linkEvent(Authentication auth, @PathVariable Long id,
                                               @RequestBody @Valid AddChampionshipEventRequest request) {
        return ChampionshipEventLinkDto.from(championshipService.linkEvent(CurrentOfficial.actor(auth), id,
                request.eventId(), request.roundNumber()));
    }

    /** Unlinks an event from the championship. {@code DELETE /{id}/events/{eventId}}; ADMIN; 204. Audited. */
    @Audited("audit_log")
    @DeleteMapping("/{id}/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkEvent(Authentication auth, @PathVariable Long id, @PathVariable Long eventId) {
        championshipService.unlinkEvent(CurrentOfficial.actor(auth), id, eventId);
    }

    /**
     * Replaces the whole points scale (points by finishing position) in one transaction (CHAMP-04).
     * {@code PUT /{id}/points-scale}; ADMIN; answers with the scale as saved. 400 for an empty list or a bad entry.
     * Audited.
     */
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

    /**
     * Fills the championship's scoring fields and points scale from a championship scoring system, which stay
     * editable afterwards. {@code POST /{id}/scoring-system/{systemId}}; ADMIN; 400 if the system is a race system;
     * 404 if either is missing. Audited with the values before and after.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/scoring-system/{systemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ChampionshipDto applyScoringSystem(Authentication auth, @PathVariable Long id, @PathVariable Long systemId) {
        return ChampionshipDto.from(championshipService.applyScoringSystem(CurrentOfficial.actor(auth), id, systemId));
    }

    /** The body of {@code PUT /{id}/split-categories}: the tags to split the standings by; empty for none. */
    public record SplitCategoriesRequest(@jakarta.validation.constraints.NotNull List<String> categories) {}

    /**
     * Sets the category tags (for example Junior, Vet, F-Grade) the championship also gives standings for, in
     * addition to its per-class and overall tables (#494). {@code PUT /{id}/split-categories}; ADMIN; 400 for more
     * than 8 tags, a tag over 30 characters or one with a comma; 404 if the championship is missing. Audited.
     */
    @Audited("audit_log")
    @PutMapping("/{id}/split-categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ChampionshipDto setSplitCategories(Authentication auth, @PathVariable Long id,
                                              @RequestBody @Valid SplitCategoriesRequest request) {
        return ChampionshipDto.from(championshipService.setSplitCategories(
                CurrentOfficial.actor(auth), id, request.categories()));
    }

    /** The drivers excluded from events of the championship. {@code GET /{id}/exclusions}; read only. */
    @GetMapping("/{id}/exclusions")
    public List<ChampionshipExclusionDto> listExclusions(@PathVariable Long id) {
        return championshipService.listExclusions(id).stream().map(ChampionshipExclusionDto::from).toList();
    }

    // Not admin-only, unlike the rest of championship setup: a referee records a disqualification (DQ), so
    // every official may add or remove an exclusion (#132). The flow is to be revisited after user testing.
    /**
     * Excludes a driver from one event of the championship, with a reason (CHAMP-02, CHAMP-09). That round is then
     * worth nothing to them, bonuses included. {@code POST /{id}/exclusions}; ADMIN, RACE_DIRECTOR or REFEREE;
     * answers 201. The recording official is the signed-in one, never taken from the body. Audited.
     */
    @Audited("audit_log")
    @PostMapping("/{id}/exclusions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChampionshipExclusionDto createExclusion(@PathVariable Long id,
                                                     Authentication auth,
                                                     @RequestBody @Valid CreateExclusionRequest request) {
        return ChampionshipExclusionDto.from(championshipService.createExclusion(CurrentOfficial.actor(auth), id,
                request.driverId(), request.eventId(), request.reason()));
    }

    /**
     * Removes an exclusion. {@code DELETE /{id}/exclusions/{exclusionId}}; ADMIN, RACE_DIRECTOR or REFEREE; 204.
     * Audited.
     */
    @Audited("audit_log")
    @DeleteMapping("/{id}/exclusions/{exclusionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExclusion(Authentication auth, @PathVariable Long id, @PathVariable Long exclusionId) {
        championshipService.deleteExclusion(CurrentOfficial.actor(auth), id, exclusionId);
    }

    /**
     * The championship's standings, worked out from the result snapshots each time. {@code GET /{id}/standings};
     * read only. Unlike the public standings, driver names are the full display names.
     */
    @GetMapping("/{id}/standings")
    public List<StandingsRowDto> getStandings(@PathVariable Long id) {
        return standingsQuery.computeStandings(id);
    }

    private static ChampionshipService.Settings settings(CreateChampionshipRequest r) {
        return new ChampionshipService.Settings(r.name(), r.bestXFromYX(), r.bestXFromYY(),
                r.tqBonusPoints(), r.afinalWinnerBonusPoints(), r.showOverallTable());
    }

    private static ChampionshipService.Settings settings(UpdateChampionshipRequest r) {
        return new ChampionshipService.Settings(r.name(), r.bestXFromYX(), r.bestXFromYY(),
                r.tqBonusPoints(), r.afinalWinnerBonusPoints(), r.showOverallTable());
    }
}
