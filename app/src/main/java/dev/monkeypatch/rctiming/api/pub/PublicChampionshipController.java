package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.query.championship.ChampionshipInfoDto;
import dev.monkeypatch.rctiming.query.championship.ChampionshipInfoQuery;
import dev.monkeypatch.rctiming.query.championship.ChampionshipStandingsQuery;
import dev.monkeypatch.rctiming.query.championship.StandingsRowDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (no-auth) endpoint for championship standings, under {@code /api/v1/championships}.
 * CHAMP-05.
 *
 * No @PreAuthorize — permitted via SecurityConfig.requestMatchers().permitAll().
 * EntityNotFoundException from ChampionshipStandingsQuery.computeStandings() is mapped to 404
 * by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/championships")
public class PublicChampionshipController {

    private final ChampionshipStandingsQuery query;
    private final ChampionshipInfoQuery infoQuery;

    private final PublicNames publicNames;

    public PublicChampionshipController(ChampionshipStandingsQuery query, ChampionshipInfoQuery infoQuery,
                                        PublicNames publicNames) {
        this.query = query;
        this.infoQuery = infoQuery;
        this.publicNames = publicNames;
    }

    /**
     * Standings for one championship, worked out on demand from the result snapshots, ranked.
     * {@code GET /api/v1/championships/{id}}, anonymous. Driver names are replaced by the public form
     * ({@link PublicNames}, #265). 404 if the championship does not exist.
     */
    @GetMapping("/{id}")
    public List<StandingsRowDto> getStandings(@PathVariable Long id) {
        PublicNames.Snapshot names = publicNames.snapshot();
        return query.computeStandings(id).stream()
                .map(r -> new StandingsRowDto(r.driverId(), names.of(r.driverId(), r.displayName(), null),
                        r.racingClassId(), r.totalPoints(), r.position(), r.rounds(), r.category()))
                .toList();
    }

    /**
     * The championship's name and the classes it scores, to head the standings tables.
     * {@code GET /api/v1/championships/{id}/info}, anonymous. 404 if the championship does not exist.
     */
    @GetMapping("/{id}/info")
    public ChampionshipInfoDto getInfo(@PathVariable Long id) {
        return infoQuery.find(id);
    }
}
