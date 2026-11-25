package dev.monkeypatch.rctiming.api.split;

import dev.monkeypatch.rctiming.modules.RequiresModule;
import dev.monkeypatch.rctiming.query.split.LapSectorQuery;
import dev.monkeypatch.rctiming.timing.split.LiveSectorService;
import dev.monkeypatch.rctiming.timing.split.SplitTimingModule;
import dev.monkeypatch.rctiming.timing.split.dto.SectorBoardDto;
import dev.monkeypatch.rctiming.timing.split.dto.SectorResultsDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public sector-time endpoints of the split-timing module (#676), anonymous like the other boards; 404 while it is off. */
@RestController
@RequestMapping("/api/v1/boards")
@RequiresModule(SplitTimingModule.ID)
public class SplitBoardController {

    private final LiveSectorService liveSectorService;
    private final LapSectorQuery lapSectorQuery;

    public SplitBoardController(LiveSectorService liveSectorService, LapSectorQuery lapSectorQuery) {
        this.liveSectorService = liveSectorService;
        this.lapSectorQuery = lapSectorQuery;
    }

    /**
     * The sector times of a race on a track with split decoders: the same board the race's
     * {@code /topic/race/{raceId}/sectors} frames carry, for a page that opens mid-race. {@code sectorCount} is 0 when
     * the club has no split decoders, so a page shows no sector columns; rows are empty until a car has a lap with
     * every split.
     */
    @GetMapping("/races/{raceId}/sectors")
    public SectorBoardDto sectors(@PathVariable long raceId) {
        return liveSectorService.boardOf(raceId);
    }

    /**
     * The sector times saved when a race finished: each car's best in every sector and every lap's sectors, for the
     * results page. {@code sectorCount} is 0 for a race with none, which is every race at a club with a single
     * decoder.
     */
    @GetMapping("/races/{raceId}/sector-results")
    public SectorResultsDto sectorResults(@PathVariable long raceId) {
        return lapSectorQuery.resultsOf(raceId);
    }
}
