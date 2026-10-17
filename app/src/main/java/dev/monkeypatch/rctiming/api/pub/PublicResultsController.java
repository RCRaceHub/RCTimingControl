package dev.monkeypatch.rctiming.api.pub;

import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (no-auth) endpoint for race result snapshots, under {@code /api/v1/results}.
 * RESULT-01, RESULT-05.
 *
 * No @PreAuthorize — permitted via SecurityConfig.requestMatchers().permitAll().
 * EntityNotFoundException from ResultSnapshotQuery.load() is mapped to 404 by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/results")
public class PublicResultsController {

    private final ResultSnapshotQuery query;
    private final PublicNames publicNames;

    public PublicResultsController(ResultSnapshotQuery query, PublicNames publicNames) {
        this.query = query;
        this.publicNames = publicNames;
    }

    /**
     * The stored result snapshot of a finished race: positions, lap history and club branding.
     * {@code GET /api/v1/results/{raceId}}, anonymous. Driver names in the positions are replaced by the public
     * form ({@link PublicNames}, #265). 404 if the race has no snapshot.
     */
    @GetMapping("/{raceId}")
    public ResultSnapshotDto get(@PathVariable Long raceId) {
        ResultSnapshotDto snapshot = query.load(raceId);
        return new ResultSnapshotDto(snapshot.raceId(), snapshot.raceLabel(), snapshot.finishedAt(),
                publicNames.rows(snapshot.positions()), snapshot.lapHistory(), snapshot.clubBranding());
    }
}
