package dev.monkeypatch.rctiming.api.boards;

import dev.monkeypatch.rctiming.api.boards.dto.BoardSettingsDto;
import dev.monkeypatch.rctiming.api.boards.dto.NowNextDto;
import dev.monkeypatch.rctiming.api.boards.dto.QualifyingBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.ResultsBoardDto;
import dev.monkeypatch.rctiming.api.boards.dto.SponsorDto;
import dev.monkeypatch.rctiming.api.boards.dto.RoundFastestLapDto;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.LiveTimingSettings;
import dev.monkeypatch.rctiming.domain.competitor.PublicNames;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.media.DriverMediaService;
import dev.monkeypatch.rctiming.domain.sponsor.EventSponsorService;
import dev.monkeypatch.rctiming.query.boards.BoardQuery;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.boards.FinalsBoardDto;
import dev.monkeypatch.rctiming.query.boards.PodiumBoardDto;
import dev.monkeypatch.rctiming.query.boards.LiveQualifyingDto;
import dev.monkeypatch.rctiming.query.boards.RaceHighlightsDto;
import dev.monkeypatch.rctiming.query.boards.TickerDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;
import dev.monkeypatch.rctiming.query.racecontrol.ResultSnapshotQuery;
import dev.monkeypatch.rctiming.service.AutoRunService;
import dev.monkeypatch.rctiming.service.BoardTickerService;
import dev.monkeypatch.rctiming.service.RaceHighlightsService;
import dev.monkeypatch.rctiming.service.FinalLegsService;
import dev.monkeypatch.rctiming.service.FinalsBoardService;
import dev.monkeypatch.rctiming.service.PodiumBoardService;
import dev.monkeypatch.rctiming.service.LiveQualifyingService;
import dev.monkeypatch.rctiming.service.QualifyingTableService;
import dev.monkeypatch.rctiming.timing.LapOrder;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LastLapTimeout;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.RaceClockService;
import dev.monkeypatch.rctiming.timing.battle.BattleGroupDto;
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
    private final FinalLegsService finalLegsService;
    private final FinalsBoardService finalsBoardService;
    private final PodiumBoardService podiumBoardService;
    private final LastLapTimeout lastLapTimeout;
    private final LiveQualifyingService liveQualifyingService;
    private final ClubProfileService clubProfileService;
    private final EventSponsorService eventSponsorService;
    private final DriverMediaService driverMediaService;
    private final RaceHighlightsService raceHighlightsService;

    public BoardController(BoardQuery boardQuery,
                           ResultSnapshotQuery resultSnapshotQuery,
                           LapTimingService lapTimingService,
                           RaceClockService raceClockService,
                           PublicNames publicNames,
                           QualifyingTableService qualifyingTableService,
                           AutoRunService autoRunService,
                           BoardTickerService boardTickerService,
                           FinalLegsService finalLegsService,
                           FinalsBoardService finalsBoardService,
                           PodiumBoardService podiumBoardService,
                           LastLapTimeout lastLapTimeout,
                           LiveQualifyingService liveQualifyingService,
                           ClubProfileService clubProfileService,
                           EventSponsorService eventSponsorService,
                           DriverMediaService driverMediaService,
                           RaceHighlightsService raceHighlightsService) {
        this.raceHighlightsService = raceHighlightsService;
        this.driverMediaService = driverMediaService;
        this.clubProfileService = clubProfileService;
        this.eventSponsorService = eventSponsorService;
        this.boardQuery = boardQuery;
        this.resultSnapshotQuery = resultSnapshotQuery;
        this.lapTimingService = lapTimingService;
        this.raceClockService = raceClockService;
        this.publicNames = publicNames;
        this.qualifyingTableService = qualifyingTableService;
        this.autoRunService = autoRunService;
        this.boardTickerService = boardTickerService;
        this.finalLegsService = finalLegsService;
        this.finalsBoardService = finalsBoardService;
        this.podiumBoardService = podiumBoardService;
        this.lastLapTimeout = lastLapTimeout;
        this.liveQualifyingService = liveQualifyingService;
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
                    return new ResultsBoardDto(event.id(), event.name(), race, resultRows(race),
                            legsOf(event.id(), race));
                })
                .orElse(new ResultsBoardDto(null, null, null, List.of(), null));
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
     * The qualifying table of the class on track with the running heat counted from its live positions (#480). When
     * no qualifying heat is running it shows the one that finished last, so the table keeps who moved, and stays up
     * as the final qualifying standings while the finals run.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/live-qualifying")
    public LiveQualifyingDto liveQualifying(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId).map(event -> {
            BoardRaceDto running = boardQuery.currentRace(event.id()).orElse(null);
            if (running != null && "QUALIFIER".equals(running.roundType())) {
                List<LiveTimingRowDto> live = lapTimingService.peek(running.raceId())
                        .map(LiveRaceState::calculatePositions).orElse(List.of());
                return liveTable(event, "LIVE", running, live);
            }
            // Not a qualifying heat on track: keep the last qualifying heat's table up, including while the finals
            // run, so the board is never empty once qualifying has been raced
            BoardRaceDto last = boardQuery.lastCompletedQualifier(event.id()).orElse(null);
            if (last != null) {
                return liveTable(event, "FINISHED", last, null);
            }
            return LiveQualifyingDto.none(event.id(), event.name());
        }).orElse(LiveQualifyingDto.none(null, null));
    }

    private LiveQualifyingDto liveTable(BoardQuery.BoardEvent event, String phase, BoardRaceDto race,
                                        List<LiveTimingRowDto> liveRows) {
        return liveQualifyingService.forHeat(race.raceId(), liveRows)
                .map(tables -> new LiveQualifyingDto(event.id(), event.name(), phase, race.raceId(), race.label(),
                        race.className(), race.roundNumber(), tables.rows(), tables.roundRows()))
                .orElse(LiveQualifyingDto.none(event.id(), event.name()));
    }

    /** @param eventId the event to show; defaults to the event racing now, else the latest in progress */
    @GetMapping("/finals")
    public FinalsBoardDto finals(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new FinalsBoardDto(event.id(), event.name(), finalsBoardService.forEventPublic(event.id())))
                .orElse(new FinalsBoardDto(null, null, List.of()));
    }

    /**
     * Each class's podium and category leaders, for the podium page.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/podium")
    public PodiumBoardDto podium(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> new PodiumBoardDto(event.id(), event.name(),
                        podiumBoardService.forEventPublic(event.id())))
                .orElse(new PodiumBoardDto(null, null, List.of()));
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
                .orElse(new TickerDto(null, List.of(), List.of(), List.of(), false, List.of()));
    }

    /**
     * The pictures and sponsors the public may see for the cars in a race (#520): headshot, car photo and the driver's
     * own sponsors. A driver whose name is not shown in full gets none (#265), and cars with nothing to show are left
     * out.
     */
    @GetMapping("/races/{raceId}/media")
    public List<DriverMediaService.PublicMedia> raceMedia(@PathVariable long raceId) {
        return List.copyOf(driverMediaService.publicForRace(raceId).values());
    }

    /**
     * The event's sponsors in display order, for the overlay's sponsor bar (#518). Names and logos only.
     *
     * @param eventId the event to show; defaults to the event racing now, else the latest in progress
     */
    @GetMapping("/sponsors")
    public List<SponsorDto> sponsors(@RequestParam(required = false) Long eventId) {
        return boardQuery.resolveEvent(eventId)
                .map(event -> eventSponsorService.list(event.id()).stream().map(SponsorDto::from).toList())
                .orElse(List.of());
    }

    /**
     * The club's thresholds the boards draw with (#505): how near the car ahead is shown as close, and how far past its
     * average lap a car is before its lap bar turns red. The battle thresholds are applied on the server, so they are
     * not here. The address options such as {@code close=} still override these on a board.
     */
    @GetMapping("/settings")
    public BoardSettingsDto settings() {
        LiveTimingSettings s = clubProfileService.liveTimingSettings();
        return new BoardSettingsDto(s.closeGapMs(), s.lateMarginMs());
    }

    /** Seeds a board's live table before the first STOMP frame arrives. */
    @GetMapping("/races/{raceId}/live-timing")
    public List<LiveTimingRowDto> liveTiming(@PathVariable long raceId) {
        return lapTimingService.peek(raceId)
                .map(LiveRaceState::calculatePositions)
                .orElse(List.of());
    }

    /**
     * The cars running close together in a race on track, with whether each gap is closing or growing (#505): the same
     * groups the race's {@code /topic/race/{raceId}/battles} frames carry, for a board or companion app that opens
     * mid-race. Empty when no cars are close or the race has no live timing.
     */
    @GetMapping("/races/{raceId}/battles")
    public List<BattleGroupDto> battles(@PathVariable long raceId) {
        return lapTimingService.battlesOf(raceId);
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
     * Time left before the race is closed by force once its clock has run out, for the countdown on the cockpit and
     * referee screens. {@code closesInMs} is null while no limit is counting down. A lap that counts puts it back,
     * so the screen asks again every few seconds.
     */
    @GetMapping("/races/{raceId}/closes-in")
    public ForcedCloseDto closesIn(@PathVariable long raceId) {
        return new ForcedCloseDto(lastLapTimeout.closesInMs(raceId).orElse(null));
    }

    /** See {@link #closesIn}. */
    public record ForcedCloseDto(Long closesInMs) {}

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

    /**
     * The standouts of this heat and of its round for the overlay's ticker: most places gained and lost against the
     * grid, and the best three and five lap average. The heat is live while it runs and from its stored result once
     * it has finished.
     */
    @GetMapping("/races/{raceId}/highlights")
    public RaceHighlightsDto highlights(@PathVariable long raceId) {
        return raceHighlightsService.forRace(raceId);
    }

    /** The A final's combined result when the race is one of its legs, matched by class name; else null. */
    private FinalLegsDto legsOf(long eventId, BoardRaceDto race) {
        if (race == null || !"A".equals(race.finalLetter())) {
            return null;
        }
        return finalLegsService.forEventPublic(eventId).stream()
                .filter(legs -> legs.className().equals(race.className())).findFirst().orElse(null);
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
