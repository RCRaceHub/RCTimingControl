package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustment;
import dev.monkeypatch.rctiming.domain.race.MarshalAdjustmentRepository;
import dev.monkeypatch.rctiming.domain.race.Penalty;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceElimination;
import dev.monkeypatch.rctiming.domain.race.RaceEliminationRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveRaceState;
import dev.monkeypatch.rctiming.timing.dto.LiveTimingRowDto;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the stored result of a race (the {@code ResultSnapshot}), the only record of a race's positions once it
 * is over. Live positions are held in memory by {@link LapTimingService} during the race and written here once, when
 * the race finishes; this is also where later corrections (penalties, marshal laps) are folded in.
 *
 * <p>Two versions of the result are kept: the one as timed, which never changes, and the corrected one that
 * championship points, exports and result screens read. Corrections are always applied to the timed one, so
 * applying them twice cannot double them. Everything here runs in the caller's write transaction, on a request
 * thread; the finish command calls {@link #snapshot} inside the same transaction as the state change.
 */
@Service
@Transactional
public class ResultSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ResultSnapshotService.class);

    private final RaceRepository raceRepository;
    private final RoundRepository roundRepository;
    private final LapTimingService lapTimingService;
    private final ResultSnapshotRepository resultSnapshotRepository;
    private final ResultSnapshotJson snapshotJson;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final PenaltyRepository penaltyRepository;
    private final MarshalAdjustmentRepository marshalAdjustmentRepository;
    private final FinalsMarshalPenalties finalsMarshalPenalties;
    private final RaceEliminationRepository raceEliminationRepository;

    public ResultSnapshotService(RaceRepository raceRepository,
                                  RoundRepository roundRepository,
                                  LapTimingService lapTimingService,
                                  ResultSnapshotRepository resultSnapshotRepository,
                                  ResultSnapshotJson snapshotJson,
                                  RaceEntryRepository raceEntryRepository,
                                  EntryRepository entryRepository,
                                  CompetitorRepository competitorRepository,
                                  PenaltyRepository penaltyRepository,
                                  MarshalAdjustmentRepository marshalAdjustmentRepository,
                                  FinalsMarshalPenalties finalsMarshalPenalties,
                                  RaceEliminationRepository raceEliminationRepository) {
        this.raceRepository = raceRepository;
        this.roundRepository = roundRepository;
        this.lapTimingService = lapTimingService;
        this.resultSnapshotRepository = resultSnapshotRepository;
        this.snapshotJson = snapshotJson;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.penaltyRepository = penaltyRepository;
        this.marshalAdjustmentRepository = marshalAdjustmentRepository;
        this.finalsMarshalPenalties = finalsMarshalPenalties;
        this.raceEliminationRepository = raceEliminationRepository;
    }

    /**
     * Persists the final race result snapshot on FINISHED transition.
     * Idempotent — upserts by raceId.
     *
     * <p>Stores the result as timed, and the result with time penalties applied (#63): lap penalties and
     * marshal lap adjustments given during the race are already in the timed laps.
     */
    public void snapshot(long raceId) {
        Race race = raceRepository.getOrThrow(raceId);
        Round round = roundRepository.findById(race.getRoundId()).orElse(null);

        List<ResultSnapshotDto.ResultRow> positions;
        List<ResultSnapshotDto.PositionAtLap> lapHistory;

        Optional<LiveRaceState> stateOpt = lapTimingService.peek(raceId);
        if (stateOpt.isPresent()) {
            LiveRaceState state = stateOpt.get();
            List<LiveTimingRowDto> rows = state.calculatePositions();

            Map<Long, EntryInfo> entryInfo = resolveEntryInfo(raceId);

            positions = new ArrayList<>();
            long raceStartMs = 0L;
            if (race.getStartType() == StartType.ROLLING && state.firstCrossingMs() > 0) {
                // A rolling start's clock waits for the first car, so total times run from its first crossing
                raceStartMs = state.firstCrossingMs();
            } else if (race.getStartedAt() != null) {
                raceStartMs = race.getStartedAt().toEpochMilli();
            } else {
                log.warn("Race {} has no startedAt — totalTimeMs will be 0 for all positions", raceId);
            }
            for (LiveTimingRowDto row : rows) {
                EntryInfo info = entryInfo.getOrDefault(row.entryId(), EntryInfo.UNKNOWN);
                long totalTimeMs = (raceStartMs > 0 && row.lastPassingTimeMs() > raceStartMs)
                        ? row.lastPassingTimeMs() - raceStartMs : 0L;
                positions.add(new ResultSnapshotDto.ResultRow(
                        row.position(),
                        row.entryId(),
                        info.competitorId(),
                        info.driverName(),
                        info.carNumber(),
                        row.lapsCompleted(),
                        totalTimeMs,
                        row.bestLapMs(),
                        row.gapToLeaderMs()
                ));
            }

            lapHistory = buildLapHistory(rows, stateOpt.orElse(null));
        } else {
            log.info("Race {} finished with no in-memory state — storing empty snapshot", raceId);
            positions = List.of();
            lapHistory = List.of();
        }

        ResultSnapshot snapshot = resultSnapshotRepository.findByRaceId(raceId)
                .orElseGet(ResultSnapshot::new);
        snapshot.setRaceId(raceId);
        snapshot.setFinishedAt(race.getFinishedAt() != null ? race.getFinishedAt() : Instant.now());

        snapshot.setTimedPositionsJson(snapshotJson.write(raceId, positions));
        snapshot.setPositionsJson(snapshotJson.write(raceId, corrected(race, snapshot.getFinishedAt(), positions)));
        snapshot.setLapHistoryJson(snapshotJson.write(raceId, lapHistory));

        resultSnapshotRepository.save(snapshot);
        // Kept until the commit: if the finish rolls back, the race is running again and needs its timing
        AfterCommit.run(() -> lapTimingService.releaseState(raceId));
        log.info("Persisted result snapshot for race {}", raceId);
    }

    /**
     * Rebuilds a finished race's result after a correction (#63): a penalty, or a marshal lap adjustment
     * given after the finish. Runs in the correction's transaction, so the results export it requests is
     * built from the corrected result.
     */
    @EventListener
    public void onFinishedRaceCorrected(FinishedRaceCorrected event) {
        applyCorrections(event.raceId());
    }

    /**
     * Recalculates the stored result from the result as timed and every correction made so far. Does nothing but
     * log if the race has no snapshot (it has not finished).
     */
    public void applyCorrections(long raceId) {
        Optional<ResultSnapshot> found = resultSnapshotRepository.findByRaceId(raceId);
        if (found.isEmpty()) {
            log.warn("Race {} has no result snapshot to correct", raceId);
            return;
        }
        Race race = raceRepository.getOrThrow(raceId);
        ResultSnapshot snapshot = found.get();
        String timedJson = snapshot.getTimedPositionsJson() != null
                ? snapshot.getTimedPositionsJson() : snapshot.getPositionsJson();
        List<ResultSnapshotDto.ResultRow> timed = snapshotJson.positions(raceId, timedJson);
        snapshot.setTimedPositionsJson(timedJson);
        snapshot.setPositionsJson(snapshotJson.write(raceId, corrected(race, snapshot.getFinishedAt(), timed)));
        resultSnapshotRepository.save(snapshot);
        log.info("Recalculated the result of race {} after a correction", raceId);
    }

    /**
     * The timed result with corrections applied. Time penalties always add time: nothing applies them
     * during the race. Lap penalties and marshal lap adjustments count only when given after the finish,
     * since those given during the race changed the live laps the timed result came from. Corrections from
     * before the race last started belong to a run that was restarted, and are left out. A marshal penalty for the
     * finals takes its lap here whenever it was given, since nothing applies it during the race.
     */
    private List<ResultSnapshotDto.ResultRow> corrected(Race race, Instant finishedAt,
                                                        List<ResultSnapshotDto.ResultRow> timed) {
        Instant startedAt = race.getStartedAt();
        Map<Long, Integer> lapDeltas = new HashMap<>();
        Map<Long, Long> addedTimeMs = new HashMap<>();

        for (Penalty penalty : penaltyRepository.findByRaceId(race.getId())) {
            Instant at = penalty.getAppliedAt();
            if (at == null || (startedAt != null && at.isBefore(startedAt))) continue;
            if (penalty.getPenaltyType() == PenaltyType.TIME) {
                long ms = penalty.getValue().multiply(BigDecimal.valueOf(1000))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                addedTimeMs.merge(penalty.getEntryId(), ms, Long::sum);
            } else if (penalty.getPenaltyType() == PenaltyType.LAP && !at.isBefore(finishedAt)) {
                lapDeltas.merge(penalty.getEntryId(), -penalty.getValue().intValue(), Integer::sum);
            }
        }
        for (MarshalAdjustment adjustment : marshalAdjustmentRepository.findByRaceIdOrderByAdjustedAt(race.getId())) {
            Instant at = adjustment.getAdjustedAt();
            if (RaceStatus.FINISHED.name().equals(adjustment.getRaceStateAtTime())
                    && at != null && !at.isBefore(finishedAt)) {
                lapDeltas.merge(adjustment.getEntryId(), adjustment.getLapDelta(), Integer::sum);
            }
        }

        // A marshal penalty for a final costs a lap in the driver's first final, whenever it was given
        finalsMarshalPenalties.lapsOffIn(race)
                .forEach((entryId, laps) -> lapDeltas.merge(entryId, -laps, Integer::sum));

        // Shikabane: the cars dead at the end, first to die first (empty for any other race)
        List<Long> dead = raceEliminationRepository.findByRaceId(race.getId()).stream()
                .filter(RaceElimination::isActive).map(RaceElimination::getEntryId).toList();

        // A marshal can credit laps to a car the decoder never saw, which has no row in the timed result,
        // and a car can die before it ever crossed the line
        List<ResultSnapshotDto.ResultRow> rows = new ArrayList<>(timed);
        Map<Long, EntryInfo> entryInfo = null;
        Set<Long> needsRow = new LinkedHashSet<>(dead);
        lapDeltas.forEach((entryId, delta) -> {
            if (delta > 0) needsRow.add(entryId);
        });
        for (long entryId : needsRow) {
            if (rows.stream().noneMatch(r -> r.entryId() == entryId)) {
                if (entryInfo == null) entryInfo = resolveEntryInfo(race.getId());
                EntryInfo info = entryInfo.getOrDefault(entryId, EntryInfo.UNKNOWN);
                rows.add(new ResultSnapshotDto.ResultRow(rows.size() + 1, entryId, info.competitorId(),
                        info.driverName(), info.carNumber(), 0, 0L, null, null));
            }
        }
        return ShikabaneOrdering.apply(ResultCorrections.apply(rows, lapDeltas, addedTimeMs), dead);
    }

    /**
     * The entry ids of a finished race's stored result, best first: the corrected result, with a Shikabane race's
     * dead cars behind the survivors. Empty when the race has no stored result. Runs in the caller's transaction.
     */
    public List<Long> finishingOrder(long raceId) {
        return resultSnapshotRepository.findByRaceId(raceId)
                .map(s -> snapshotJson.positions(raceId, s.getPositionsJson()).stream()
                        .map(ResultSnapshotDto.ResultRow::entryId).toList())
                .orElse(List.of());
    }

    /** Removes the race's stored result, such as when the race is restarted to run again. A no-op if there is none. */
    public void deleteByRaceId(long raceId) {
        resultSnapshotRepository.findByRaceId(raceId)
                .ifPresent(resultSnapshotRepository::delete);
        log.info("Deleted result snapshot for race {}", raceId);
    }

    /** The driver and car number recorded against an entry in the result snapshot. */
    private record EntryInfo(Long competitorId, String driverName, String carNumber) {
        static final EntryInfo UNKNOWN = new EntryInfo(null, "Unknown", null);
    }

    private Map<Long, EntryInfo> resolveEntryInfo(long raceId) {
        List<RaceEntry> raceEntries = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId);
        return raceEntries.stream()
                .collect(Collectors.toMap(
                        RaceEntry::getEntryId,
                        re -> {
                            Optional<Entry> entry = entryRepository.findById(re.getEntryId());
                            if (entry.isEmpty()) return EntryInfo.UNKNOWN;
                            Long competitorId = entry.get().getCompetitorId();
                            String name = Optional.ofNullable(competitorId)
                                    .flatMap(competitorRepository::findById)
                                    .map(Competitor::getDisplayName)
                                    .orElse("Unknown");
                            String carNum = re.getCarNumber() != null ? re.getCarNumber().toString() : null;
                            return new EntryInfo(competitorId, name, carNum);
                        },
                        (a, b) -> a  // keep first on duplicate real ID (defensive)
                ));
    }

    /**
     * Builds a lap history: for each lap, where each car stood when it completed that lap, with its lap time.
     * The place at a lap is the order the cars crossed the line to complete it (see {@link #positionsAfterLap}), so a
     * position chart shows who passed whom. Where the crossing times can't be worked out (no live state, or a lap
     * count changed by a marshal adjustment, for any car) every place falls back to the final position. A lapped car's
     * line ends where it stopped counting laps. A correction after the race rebuilds this from the live state if it
     * is still held, otherwise it keeps the stored history.
     * The state parameter is nullable: legacy snapshots or races without live state get null lapTimeMs.
     */
    private List<ResultSnapshotDto.PositionAtLap> buildLapHistory(List<LiveTimingRowDto> rows, LiveRaceState state) {
        List<ResultSnapshotDto.PositionAtLap> history = new ArrayList<>();
        int leaderLaps = rows.stream().mapToInt(LiveTimingRowDto::lapsCompleted).max().orElse(0);
        if (leaderLaps == 0) return history;

        Map<Long, LapClock> clocks = new HashMap<>();
        if (state != null) {
            for (LiveTimingRowDto row : rows) {
                List<Long> lapTimes = state.lapTimesSnapshot(row.entryId());
                if (!lapTimes.isEmpty()) {
                    clocks.put(row.entryId(), new LapClock(row.lastPassingTimeMs(), lapTimes));
                }
            }
        }
        // Decided once for the whole race, so one car's place never comes from a different rule than another's
        // within a lap, or between laps.
        boolean orderUsable = rows.stream().allMatch(r -> r.lapsCompleted() == 0
                || (clocks.get(r.entryId()) != null && clocks.get(r.entryId()).lapTimes().size() == r.lapsCompleted()));
        Map<Long, LapClock> orderClocks = orderUsable ? clocks : Map.of();

        for (int lap = 1; lap <= leaderLaps; lap++) {
            Map<Long, Integer> placeAtLap = positionsAfterLap(lap, rows, orderClocks);
            for (LiveTimingRowDto row : rows) {
                if (row.lapsCompleted() >= lap) {
                    LapClock clock = clocks.get(row.entryId());
                    Long lapTimeMs = clock != null && lap <= clock.lapTimes().size() ? clock.lapTimes().get(lap - 1) : null;
                    history.add(new ResultSnapshotDto.PositionAtLap(lap, row.entryId(),
                            placeAtLap.getOrDefault(row.entryId(), row.position()), lapTimeMs));
                }
            }
        }
        return history;
    }

    /**
     * A car's lap times and the time of its last counted passing, which is all that is needed to work out when it
     * completed each lap. Times are in milliseconds, the passings' UTC time.
     */
    record LapClock(long lastPassingTimeMs, List<Long> lapTimes) {

        /** Time the car crossed the line to complete {@code lap} (1-based): its last passing minus the laps after it. */
        long completedLapAtMs(int lap) {
            long after = 0;
            for (int i = lap; i < lapTimes.size(); i++) {
                after += lapTimes.get(i);
            }
            return lastPassingTimeMs - after;
        }
    }

    /**
     * The place of each car that completed {@code lap}, by the order they crossed the line to complete it: 1 is first
     * across. Cars that have not completed the lap are not placed. If any car that completed the lap has no usable
     * lap times (none recorded, or a count that differs from its laps because a marshal adjustment changed it) the
     * crossing order can't be trusted, so every car falls back to its final position.
     */
    static Map<Long, Integer> positionsAfterLap(int lap, List<LiveTimingRowDto> rows, Map<Long, LapClock> clocks) {
        List<LiveTimingRowDto> completed = rows.stream().filter(r -> r.lapsCompleted() >= lap).toList();
        boolean usable = completed.stream().allMatch(r -> {
            LapClock clock = clocks.get(r.entryId());
            return clock != null && clock.lapTimes().size() == r.lapsCompleted();
        });
        Map<Long, Integer> places = new HashMap<>();
        if (!usable) {
            completed.forEach(r -> places.put(r.entryId(), r.position()));
            return places;
        }
        List<LiveTimingRowDto> crossing = new ArrayList<>(completed);
        crossing.sort(Comparator
                .comparingLong((LiveTimingRowDto r) -> clocks.get(r.entryId()).completedLapAtMs(lap))
                .thenComparingInt(LiveTimingRowDto::position));
        for (int i = 0; i < crossing.size(); i++) {
            places.put(crossing.get(i).entryId(), i + 1);
        }
        return places;
    }
}
