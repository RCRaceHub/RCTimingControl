package dev.monkeypatch.rctiming.timing.split;

import dev.monkeypatch.rctiming.domain.race.RaceRestartedEvent;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.LiveTimingHub;
import dev.monkeypatch.rctiming.timing.dto.SectorBoardDto;
import dev.monkeypatch.rctiming.timing.dto.SectorRowDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the latest and best sector time of every car in the running race and broadcasts them (#676).
 *
 * <p>{@link SectorTimingService} publishes a {@link SectorTimesEvent} for each lap that has every split; this class
 * turns the transponder into an entry (the same way {@link LapTimingService} does, runtime links first), updates that
 * car's last and best sectors and the race's overall best, and sends the whole board on
 * {@code /topic/race/{raceId}/sectors}. It runs on the timing thread; the boards endpoint reads from a request thread,
 * so every access to the state is synchronised. Like live positions, the sectors are held in memory only, and only for
 * the most recent race: a passing for another race starts the board afresh.
 */
@Service
public class LiveSectorService {

    private static final Logger log = LoggerFactory.getLogger(LiveSectorService.class);

    /** The sectors of one race. */
    private static final class RaceSectors {
        final long raceId;
        final int sectorCount;
        final Map<Long, SectorRowDto> rows = new HashMap<>();
        /** Every lap's sectors per entry, in the order driven, for saving when the race finishes. */
        final Map<Long, List<LapSectorRepository.Row>> saved = new HashMap<>();
        final long[] overallBest;

        RaceSectors(long raceId, int sectorCount) {
            this.raceId = raceId;
            this.sectorCount = sectorCount;
            this.overallBest = new long[sectorCount];
        }
    }

    private final LapTimingService lapTimingService;
    private final LiveTimingHub liveTimingHub;
    private final SplitDecoderRepository splitDecoders;
    private final LapSectorRepository lapSectors;

    private RaceSectors current;

    public LiveSectorService(LapTimingService lapTimingService, LiveTimingHub liveTimingHub,
                             SplitDecoderRepository splitDecoders, LapSectorRepository lapSectors) {
        this.lapTimingService = lapTimingService;
        this.liveTimingHub = liveTimingHub;
        this.splitDecoders = splitDecoders;
        this.lapSectors = lapSectors;
    }

    /** Records one lap's sector times against the car's entry and broadcasts the race's board. Timing thread. */
    @EventListener
    public void onSectorTimes(SectorTimesEvent event) {
        Optional<Long> entryId;
        try {
            entryId = lapTimingService.entryOf(event.raceId(), event.transponderNumber());
        } catch (RuntimeException e) {
            // A fault here must never cost the finish passing that is being credited at the same moment
            log.error("Could not work out whose sector times these are (transponder {})", event.transponderNumber(), e);
            return;
        }
        if (entryId.isEmpty()) {
            log.debug("Sector times for transponder {} dropped: no entry in race {}", event.transponderNumber(),
                    event.raceId());
            return;
        }
        SectorBoardDto board;
        synchronized (this) {
            if (current == null || current.raceId != event.raceId()
                    || current.sectorCount != event.sectorMicros().size()) {
                current = new RaceSectors(event.raceId(), event.sectorMicros().size());
            }
            List<Long> last = new ArrayList<>();
            for (long micros : event.sectorMicros()) {
                last.add(Math.round(micros / 1000.0));
            }
            SectorRowDto before = current.rows.get(entryId.get());
            List<Long> best = new ArrayList<>();
            for (int i = 0; i < last.size(); i++) {
                long mine = last.get(i);
                long previous = before == null ? mine : before.bestMs().get(i);
                best.add(Math.min(mine, previous));
                if (current.overallBest[i] == 0 || mine < current.overallBest[i]) {
                    current.overallBest[i] = mine;
                }
            }
            current.rows.put(entryId.get(), new SectorRowDto(entryId.get(), event.lapNumber(), last, best));
            List<LapSectorRepository.Row> lapRows = current.saved.computeIfAbsent(entryId.get(), k -> new ArrayList<>());
            for (int i = 0; i < last.size(); i++) {
                lapRows.add(new LapSectorRepository.Row(entryId.get(), event.lapNumber(), i + 1, last.get(i)));
            }
            board = boardOfCurrent();
        }
        liveTimingHub.broadcastSectors(event.raceId(), board);
    }

    /**
     * A race that finishes has every lap's sector times saved to {@code lap_sectors} (#676), for its results. Runs on
     * the thread that finished the race, inside that command's transaction; a fault saving them is logged and never
     * fails the finish.
     */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        if (event.getNewStatus() != RaceStatus.FINISHED) {
            return;
        }
        long raceId = event.getRaceId();
        List<LapSectorRepository.Row> rows = new ArrayList<>();
        synchronized (this) {
            if (current != null && current.raceId == raceId) {
                current.saved.values().forEach(rows::addAll);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        try {
            lapSectors.replaceForRace(raceId, rows);
        } catch (RuntimeException e) {
            log.error("Could not save the sector times of race {}", raceId, e);
        }
    }

    /**
     * A race that is restarted starts its sector board afresh: the old rows and bests are dropped, any sectors saved
     * for the race are removed and an empty board is broadcast, so a screen does not keep the last run's times. Runs
     * on the thread that restarted the race, inside that command's transaction; a fault removing the saved sectors is
     * logged and never fails the restart.
     */
    @EventListener
    public void onRaceRestarted(RaceRestartedEvent event) {
        long raceId = event.raceId();
        try {
            lapSectors.deleteForRace(raceId);
        } catch (RuntimeException e) {
            log.error("Could not remove the saved sector times of race {}", raceId, e);
        }
        SectorBoardDto empty = null;
        synchronized (this) {
            if (current != null && current.raceId == raceId) {
                empty = new SectorBoardDto(current.sectorCount, List.of(), List.of());
                current = null;
            }
        }
        if (empty != null) {
            liveTimingHub.broadcastSectors(raceId, empty);
        }
    }

    /**
     * Drops the board when the split decoders change, once the change has committed: times recorded against the old
     * set of splits no longer line up with the sectors of a lap. The next lap with every split starts it again.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void onDecodersChanged(SplitDecodersChangedEvent event) {
        current = null;
    }

    /**
     * The race's sector board for a client that opens mid-race. {@code sectorCount} is how many sectors a lap has now,
     * from the enabled split decoders, so a page can lay its columns out before the first lap has sectors; the rows
     * and bests are those of the latest race with sector times, and empty for any other. Request thread.
     */
    public SectorBoardDto boardOf(long raceId) {
        long splits = splitDecoders.findAll().stream().filter(SplitDecoder::enabled).count();
        int sectorCount = splits == 0 ? 0 : (int) splits + 1;
        synchronized (this) {
            if (current == null || current.raceId != raceId) {
                return new SectorBoardDto(sectorCount, List.of(), List.of());
            }
            SectorBoardDto board = boardOfCurrent();
            return new SectorBoardDto(sectorCount, board.overallBestMs(), board.rows());
        }
    }

    /** Must be called while holding the lock. */
    private SectorBoardDto boardOfCurrent() {
        List<Long> overall = new ArrayList<>();
        for (long best : current.overallBest) {
            overall.add(best == 0 ? null : best);
        }
        return new SectorBoardDto(current.sectorCount, overall, new ArrayList<>(current.rows.values()));
    }
}
