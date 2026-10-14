package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.query.championship.ChampionshipStandingsQuery;
import dev.monkeypatch.rctiming.query.championship.StandingsRowDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (no-auth) endpoint for championship standings.
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

    private final PublicNames publicNames;

    public PublicChampionshipController(ChampionshipStandingsQuery query, PublicNames publicNames) {
        this.query = query;
        this.publicNames = publicNames;
    }

    @GetMapping("/{id}")
    public List<StandingsRowDto> getStandings(@PathVariable Long id) {
        PublicNames.Snapshot names = publicNames.snapshot();
        return query.computeStandings(id).stream()
                .map(r -> new StandingsRowDto(r.driverId(), names.of(r.driverId(), r.displayName(), null),
                        r.racingClassId(), r.totalPoints(), r.rounds()))
                .toList();
    }
}
