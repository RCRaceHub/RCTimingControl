package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAudit;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Links an unknown transponder to an entry during a live race (TIMING-08), crediting every passing it has
 * made since the start. The number is also saved on the driver's entry, so it counts for the rest of the event
 * without being linked again.
 */
@Service
@Transactional
public class TransponderLinkService {

    private static final Logger log = LoggerFactory.getLogger(TransponderLinkService.class);

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final LapTimingService lapTimingService;
    private final UnknownTransponderLinkAuditRepository linkAuditRepository;
    private final AuditService audit;
    private final RaceAuditLabels labels;
    private final EntryRepository entryRepository;
    private final TransponderSwapService swapService;

    public TransponderLinkService(RaceRepository raceRepository,
                                  RaceEntryRepository raceEntryRepository,
                                  LapTimingService lapTimingService,
                                  UnknownTransponderLinkAuditRepository linkAuditRepository,
                                  AuditService audit,
                                  RaceAuditLabels labels,
                                  EntryRepository entryRepository,
                                  TransponderSwapService swapService) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.lapTimingService = lapTimingService;
        this.linkAuditRepository = linkAuditRepository;
        this.audit = audit;
        this.labels = labels;
        this.entryRepository = entryRepository;
        this.swapService = swapService;
    }

    /**
     * Links the transponder to an entry in a running or stopped race and credits its passings to that entry.
     * Linking it again to the same entry credits nothing more; linking it to a different one is refused, since
     * its laps are already credited to the first.
     *
     * <p>The audit rows are written first and the live timing is changed only once they commit, so a link is
     * never live without its record (#217). The laps credited are counted before the commit; a passing from
     * this transponder that arrives in between is credited too, but is not in that count. With one write
     * connection, the next link request starts only after this one is live, so its checks see it.
     *
     * <p>The number is saved on the entry in the same transaction (see {@link #saveOnEntry}), so later races at
     * the event find it by themselves.
     *
     * @return how many laps the link credited
     */
    public int link(long raceId, String transponderNumber, long entryId, long userId) {
        Race race = raceRepository.getOrThrow(raceId);
        if (race.getStatus() != RaceStatus.RUNNING && race.getStatus() != RaceStatus.STOPPED) {
            throw new StateConflictException("Transponders can only be linked while the race is running or stopped");
        }
        boolean inRace = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId).stream()
                .anyMatch(raceEntry -> raceEntry.getEntryId() == entryId);
        if (!inRace) {
            throw new IllegalArgumentException("That entry is not in this race");
        }
        Long linkedTo = lapTimingService.stateFor(raceId).getRuntimeLink(transponderNumber);
        if (linkedTo != null) {
            if (linkedTo == entryId) {
                return 0;
            }
            throw new StateConflictException("Transponder " + transponderNumber
                    + " is already linked to another entry in this race");
        }

        // Counted before linking, since linking credits them. The first passing only starts timing a lap
        int lapsCredited = Math.max(0, lapTimingService.countPassingsForTransponder(raceId, transponderNumber) - 1);

        linkAuditRepository.save(new UnknownTransponderLinkAudit(raceId, transponderNumber, entryId, userId));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("transponderNumber", transponderNumber);
        after.put("entryId", entryId);
        after.put("lapsCredited", lapsCredited);
        audit.entry(Actor.official(userId), "UNKNOWN_TRANSPONDER_LINKED")
                .entity("race", raceId).race(raceId).event(labels.eventOf(raceId))
                .summary("Linked transponder " + transponderNumber + " to " + labels.driver(entryId) + " in "
                        + labels.race(raceId) + ", crediting " + lapsCredited + " laps")
                .after(after).record();

        saveOnEntry(raceId, transponderNumber, entryId, userId);

        AfterCommit.run(() -> lapTimingService.linkTransponder(raceId, transponderNumber, entryId));
        return lapsCredited;
    }

    /**
     * Keeps the linked number on the entry, through a transponder swap so it is audited the same way: in place
     * of the primary when it has not passed in this race and there is no secondary or the secondary has,
     * otherwise in the secondary slot (empty, or the one that has not passed). A number another competitor's entry holds is only linked for this
     * race, as a swap would refuse it. The entry is changed only if its number is not already the entry's.
     */
    private void saveOnEntry(long raceId, String transponderNumber, long entryId, long userId) {
        Long eventId = labels.eventOf(raceId);
        Entry entry = entryRepository.findById(entryId).orElse(null);
        if (eventId == null || entry == null || transponderNumber.equals(entry.getTransponderNumberSnapshot())
                || transponderNumber.equals(entry.getSecondaryTransponderNumber())) {
            return;
        }
        boolean primarySeen = lapTimingService.countPassingsForTransponder(raceId, entry.getTransponderNumberSnapshot()) > 0;
        String secondary = entry.getSecondaryTransponderNumber();
        boolean secondarySeen = secondary != null && !secondary.isBlank()
                && lapTimingService.countPassingsForTransponder(raceId, secondary) > 0;
        boolean hasSecondary = secondary != null && !secondary.isBlank();
        TransponderSlot slot = !primarySeen && (!hasSecondary || secondarySeen)
                ? TransponderSlot.PRIMARY : TransponderSlot.SECONDARY;
        SwapResult result = swapService.swap(eventId, entryId, slot, transponderNumber, userId);
        if (!(result instanceof SwapResult.Success)) {
            log.info("Transponder {} linked to entry {} for race {} only: not saved on the entry ({})",
                    transponderNumber, entryId, raceId, result.getClass().getSimpleName());
        }
    }
}
