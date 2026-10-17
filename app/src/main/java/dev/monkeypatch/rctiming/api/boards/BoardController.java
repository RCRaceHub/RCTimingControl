package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.NowNextDto;
import dev.monkeypatch.rctiming.api.boards.dto.QualifyingBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.ResultsBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.RoundFastestLapDto;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import dev.monkeypatch.rctiming.service.QualifyingTableService;
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
 * Anonymous read API for the spectator boards on a venue TV (L12), under {@code /api/v1/boards}. No
 * @PreAuthorize: GET /api/v1/boards/** is permitAll in SecurityConfig.
 * Live updates come from the race's STOMP topics, which anonymous clients may subscribe to.
 * Every endpoint is read only, and driver names are given in the form the public may see (#265).
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

    public BoardController(BoardQuery boardQuery,
                           ResultSnapshotQuery resultSnapshotQuery,
                           LapTimingService lapTimingService,
                           RaceClockService raceClockService,
                           PublicNames publicNames,
                           QualifyingTableService qualifyingTableService) {
        this.boardQuery = boardQuery;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.lapTimingService = lapTimingService;
        this.raceClockService = raceClockService;
        this.publicNames = publicNames;
        this.qualifyingTableService = qualifyingTableService;
    }

    /**
     * The now/next board: the event's current, next and last completed race. An unknown event, or no event in
     * progress, gives an empty {@link NowNextDto} rather than a 404.
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
                        boardQuery.lastCompletedRace(event.id()).orElse(null)))
                .orElse(new NowNextDto(null, null, null, null, null));
    }

    /**
     * The results board: the event's last finished race with its result rows. The rows are empty if that race has
     * no snapshot yet, and the whole board is empty when no event is found, never a 404.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/results")
    public ResultsBoardDto results(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> {
                    BoardRaceDto race = boardQuery.lastCompletedRace(event.id()).orElse(null);
                    return new ResultsBoardDto(event.id(), event.name(), race, resultRows(race));
                })
                .orElse(new ResultsBoardDto(null, null, null, List.of()));
    }

    /**
     * The qualifying board: the public qualifying table of each class of the event. Empty when no event is found,
     * never a 404.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/qualifying")
    public QualifyingBoardDto qualifying(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new QualifyingBoardDto(event.id(), event.name(),
                        qualifyingTableService.forEventPublic(event.id())))
                .orElse(new QualifyingBoardDto(null, null, List.of()));
    }

    /**
     * Seeds a board's live table before the first STOMP frame arrives: the current positions of the race, worked
     * out from its in-memory state. Empty if the race is not being timed (not loaded in memory), not a 404.
     */
    @GetMapping("/races/{raceId}/live-timing")
    public List<LiveTimingRowDto> liveTiming(@PathVariable long raceId) {
        return lapTimingService.peek(raceId)
                .map(LiveRaceState::calculatePositions)
                .orElse(List.of());
    }

    /**
     * The race clock, for the streaming overlay (#29); a viewer counts on locally while it is running. 404 if the
     * race does not exist.
     */
    @GetMapping("/races/{raceId}/clock")
    public RaceClockDto clock(@PathVariable long raceId) {
        return raceClockService.clock(raceId)
                .orElseThrow(() -> new EntityNotFoundException("Race not found: " + raceId));
    }

    /**
     * The fastest lap in the other finished heats of this race's round and class, for the overlay's ticker (the
     * running heat's own best comes from its live timing). Finished heats with no snapshot yet count for nothing,
     * and the answer has null fields when there is no lap to show.
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
