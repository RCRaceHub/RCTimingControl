package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.NowNextDto;
import dev.monkeypatch.rctiming.api.boards.dto.ResultsBoardDto;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.timing.dto.RaceClockDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anonymous read API for the spectator boards on a venue TV (L12). No
 * @PreAuthorize: GET /api/v1/boards/** is permitAll in SecurityConfig.
 * Live updates come from the race's STOMP topics, which anonymous clients may subscribe to.
 */
@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

    private final BoardQuery boardQuery;
    private final ResultSnapshotQuery resultSnapshotQuery;
    private final LapTimingService lapTimingService;
    private final RaceClockService raceClockService;
    private final PublicNames publicNames;

    public BoardController(BoardQuery boardQuery,
                           ResultSnapshotQuery resultSnapshotQuery,
                           LapTimingService lapTimingService,
                           RaceClockService raceClockService,
                           PublicNames publicNames) {
        this.boardQuery = boardQuery;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.lapTimingService = lapTimingService;
        this.raceClockService = raceClockService;
        this.publicNames = publicNames;
    }

    /** @param eventId the event to show; defaults to the event racing now, else the latest in progress */
    @GetMapping("/now-next")
    public NowNextDto nowNext(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new NowNextDto(
                        event.id(),
                        event.name(),
                        boardQuery.currentRace(event.id()).orElse(null),
                        boardQuery.nextRace(event.id()).orElse(null),
                        boardQuery.lastCompletedRace(event.id()).orElse(null)))
                .orElse(new NowNextDto(null, null, null, null, null));
    }

    /** @param eventId the event to show; defaults to the event racing now, else the latest in progress */
    @GetMapping("/results")
    public ResultsBoardDto results(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> {
                    BoardRaceDto race = boardQuery.lastCompletedRace(event.id()).orElse(null);
                    return new ResultsBoardDto(event.id(), event.name(), race, resultRows(race));
                })
                .orElse(new ResultsBoardDto(null, null, null, List.of()));
    }

    /** Seeds a board's live table before the first STOMP frame arrives. */
    @GetMapping("/races/{raceId}/live-timing")
    public List<LiveTimingRowDto> liveTiming(@PathVariable long raceId) {
        return lapTimingService.peek(raceId)
                .map(LiveRaceState::calculatePositions)
                .orElse(List.of());
    }

    /** The race clock, for the streaming overlay (#29); a viewer counts on locally while it is running. */
    @GetMapping("/races/{raceId}/clock")
    public RaceClockDto clock(@PathVariable long raceId) {
        return raceClockService.clock(raceId)
                .orElseThrow(() -> new EntityNotFoundException("Race not found: " + raceId));
    }

    private List<ResultSnapshotDto.ResultRow> resultRows(BoardRaceDto race) {
        if (race == null) {
            return List.of();
        }
        try {
            return publicNames.rows(resultSnapshotQuery.load(race.raceId()).positions());
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
    }
}
