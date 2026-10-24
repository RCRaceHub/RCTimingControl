package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RaceStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The warm-up lap check (#482): which cars of a race on the grid the decoder has already seen, so the director can
 * tell a flat or missing transponder before the start rather than after it.
 *
 * <p>Passings that arrive while no race is running ({@link LapPassingEvent#NO_RACE}) and a race is called to the
 * grid are matched to that race's entries by their primary or secondary number. Nothing is timed or stored: the
 * seen entries are kept in memory and forgotten when the race leaves the grid (it starts, is taken back or is
 * restarted). A number held by more than one entry in the race marks all of them seen, since this is only a presence
 * check; the race itself still sends such a passing to the referee.
 *
 * <p>Runs on the single timing thread, like {@link LapTimingService}, so a lookup of the grid race and its entries
 * costs one read per unmatched passing; the broadcast on {@code /topic/race/{id}/warmup} happens only when a car is
 * seen for the first time.
 */
@Service
public class WarmUpCheckService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final LiveTimingHub liveTimingHub;
    private final Map<Long, Set<Long>> seen = new ConcurrentHashMap<>();

    public WarmUpCheckService(RaceRepository raceRepository, RaceEntryRepository raceEntryRepository,
                              EntryRepository entryRepository, LiveTimingHub liveTimingHub) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.liveTimingHub = liveTimingHub;
    }

    /**
     * Notes a car seen on the warm-up lap. Ignores a passing made while a race is running and one made while no race
     * is on the grid. Runs on the timing thread.
     */
    @EventListener(LapPassingEvent.class)
    public void onLapPassing(LapPassingEvent event) {
        if (event.raceId() != LapPassingEvent.NO_RACE) {
            return;
        }
        Race grid = raceRepository.findFirstByStatus(RaceStatus.GRID).orElse(null);
        if (grid == null) {
            return;
        }
        List<Long> entryIds = raceEntryRepository.findByRaceIdOrderByGridPosition(grid.getId()).stream()
                .map(RaceEntry::getEntryId)
                .toList();
        Set<Long> seenHere = seen.computeIfAbsent(grid.getId(), id -> ConcurrentHashMap.newKeySet());
        boolean added = false;
        for (Entry entry : entryRepository.findAllById(entryIds)) {
            if (entry.getStatus() != EntryStatus.WITHDRAWN
                    && (event.transponderNumber().equals(entry.getTransponderNumberSnapshot())
                        || event.transponderNumber().equals(entry.getSecondaryTransponderNumber()))) {
                added |= seenHere.add(entry.getId());
            }
        }
        if (added) {
            liveTimingHub.broadcastWarmUp(grid.getId(), seenEntryIds(grid.getId()));
        }
    }

    /** The entries of the race seen so far, in id order; empty when none or the race is not on the grid. */
    public List<Long> seenEntryIds(long raceId) {
        return List.copyOf(new TreeSet<>(seen.getOrDefault(raceId, Collections.emptySet())));
    }

    /** Starts a race's check afresh when it is called to the grid, and drops it once it leaves the grid. */
    @EventListener
    public void onRaceStatusChanged(RaceStatusChangedEvent event) {
        seen.remove(event.getRaceId());
    }
}
