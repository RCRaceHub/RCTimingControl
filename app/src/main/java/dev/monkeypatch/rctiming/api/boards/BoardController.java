package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.NowNextDto;
import dev.monkeypatch.rctiming.api.boards.dto.QualifyingBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.ResultsBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.RoundFastestLapDto;
import dev.monkeypatch.rctiming.api.boards.dto.TickerDto;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import dev.monkeypatch.rctiming.service.AutoRunService;
import dev.monkeypatch.rctiming.service.BoardTickerService;
import dev.monkeypatch.rctiming.service.QualifyingTableService;
import dev.monkeypatch.rctiming.timing.LapOrder;
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
 * Anonymous read API for the spectator boards on a venue TV (L12). It has no {@code @PreAuthorize}:
 * GET /api/v1/boards/** is permitAll in SecurityConfig.
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
    private final QualifyingTableService qualifyingTableService;
    private final AutoRunService autoRunService;
    private final BoardTickerService boardTickerService;

    public BoardController(BoardQuery boardQuery,
                           ResultSnapshotQuery resultSnapshotQuery,
                           LapTimingService lapTimingService,
                           RaceClockService raceClockService,
                           PublicNames publicNames,
                           QualifyingTableService qualifyingTableService,
                           AutoRunService autoRunService,
                           BoardTickerService boardTickerService) {
        this.boardQuery = boardQuery;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.lapTimingService = lapTimingService;
        this.raceClockService = raceClockService;
        this.publicNames = publicNames;
        this.qualifyingTableService = qualifyingTableService;
        this.autoRunService = autoRunService;
        this.boardTickerService = boardTickerService;
    }

    /**
     * The race on track, the next and the last finished, plus auto run's countdown when it is on.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/now-next")
    public NowNextDto nowNext(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new NowNextDto(
                        event.id(),
                        event.name(),
                        boardQuery.currentRace(event.id()).orElse(null),
                        boardQuery.nextRace(event.id()).orElse(null),
                        boardQuery.lastCompletedRace(event.id()).orElse(null),
                        autoRunService.boardView(event.id()).orElse(null)))
                .orElse(new NowNextDto(null, null, null, null, null, null));
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

    /** @param eventId the event to show; defaults to the event racing now, else the latest in progress */
    @GetMapping("/qualifying")
    public QualifyingBoardDto qualifying(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new QualifyingBoardDto(event.id(), event.name(),
                        qualifyingTableService.forEventPublic(event.id())))
                .orElse(new QualifyingBoardDto(null, null, List.of()));
    }

    /**
     * What the overlay's ticker scrolls: the next race, its drivers and marshals, and each class's top qualifier.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/ticker")
    public TickerDto ticker(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> boardTickerService.forEvent(event.id()))
                .orElse(new TickerDto(null, List.of(), List.of(), List.of()));
    }

    /** Seeds a board's live table before the first STOMP frame arrives. */
    @GetMapping("/races/{raceId}/live-timing")
    public List<LiveTimingRowDto> liveTiming(@PathVariable long raceId) {
        return lapTimingService.peek(raceId)
                .map(LiveRaceState::calculatePositions)
                .orElse(List.of());
    }

    /**
     * Where each car stood at the end of every lap so far in a race on track, for the live position chart. Empty
     * when the race has no live state (not started, or already finished: its stored result has the history).
     */
    @GetMapping("/races/{raceId}/position-history")
    public List<LapOrder.Place> positionHistory(@PathVariable long raceId) {
        return lapTimingService.peek(raceId)
                .map(state -> state.lapOrder().places())
                .orElse(List.of());
    }

    /** The race clock, for the streaming overlay (#29); a viewer counts on locally while it is running. */
    @GetMapping("/races/{raceId}/clock")
    public RaceClockDto clock(@PathVariable long raceId) {
        return raceClockService.clock(raceId)
                .orElseThrow(() -> new EntityNotFoundException("Race not found: " + raceId));
    }

    /**
     * The fastest lap in the other finished heats of this race's round and class, for the overlay's ticker (the
     * running heat's own best comes from its live timing).
     */
    @GetMapping("/races/{raceId}/round-fastest-lap")
    public RoundFastestLapDto roundFastestLap(@PathVariable long raceId) {
        ResultSnapshotDto.ResultRow fastest = null;
        for (long finishedId : boardQuery.finishedRaceIdsInRound(raceId)) {
            for (ResultSnapshotDto.ResultRow row : resultRows(finishedId)) {
                if (row.bestLapMs() != null && row.bestLapMs() > 0
                        && (fastest == null || row.bestLapMs() < fastest.bestLapMs())) {
                    fastest = row;
                }
            }
        }
        return fastest == null
                ? new RoundFastestLapDto(null, null)
                : new RoundFastestLapDto(fastest.bestLapMs(), fastest.driverName());
    }

    private List<ResultSnapshotDto.ResultRow> resultRows(BoardRaceDto race) {
        return race == null ? List.of() : resultRows(race.raceId());
    }

    private List<ResultSnapshotDto.ResultRow> resultRows(long raceId) {
        try {
            return publicNames.rows(resultSnapshotQuery.load(raceId).positions());
        } catch (EntityNotFoundException e) {
            // Finished but not yet snapshotted
            return List.of();
        }
    }
}
