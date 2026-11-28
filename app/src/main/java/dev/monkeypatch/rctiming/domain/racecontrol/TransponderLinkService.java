package dev.monkeypatch.rctiming.domain.racecontrol;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.transponder.SwapResult;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSlot;
import dev.monkeypatch.rctiming.domain.transponder.TransponderSwapService;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAudit;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAuditRepository;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Links an unknown transponder to an entry during a live race (TIMING-08), crediting every passing it has
 * made since the start. The number is also saved on the driver's entry, so it counts for the rest of the event
 * without being linked again.
 *
 * <p>It also decides the wrong-heat flags (see {@link #approveWrongHeat} and {@link #disapproveWrongHeat}), raised
 * when a number of a driver who is in another heat is seen.
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
     * @throws StateConflictException   if the race is not running or stopped, or the number is linked to another
     *                                  entry already
     * @throws IllegalArgumentException if the entry is not in the race
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
        Long eventId = labels.eventOf(raceId);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("transponderNumber", transponderNumber);
        after.put("entryId", entryId);
        after.put("lapsCredited", lapsCredited);
        audit.entry(Actor.official(userId), "UNKNOWN_TRANSPONDER_LINKED")
                .entity("race", raceId).race(raceId).event(eventId)
                .summary("Linked transponder " + transponderNumber + " to " + labels.driver(entryId) + " in "
                        + labels.race(raceId) + ", crediting " + lapsCredited + " laps")
                .after(after).record();

        saveOnEntry(raceId, eventId, transponderNumber, entryId, userId);

        AfterCommit.run(() -> lapTimingService.linkTransponder(raceId, transponderNumber, entryId));
        return lapsCredited;
    }

    /**
     * Lets a driver seen in the wrong heat count in this race: puts them on the race (at the back of the grid, with
     * their own car number when it is free) and links the number to them, which credits every passing it has made.
     * They are ranked by their laps like everyone else. Allowed from a flag that was disapproved, so the decision
     * can be reversed. The audit row, the race entry and the link are one transaction; the live timing changes once
     * it commits.
     *
     * @return how many laps the approval credited, 0 if the flag was approved already
     * @throws StateConflictException if the race is not running or stopped, or there is no such flag
     */
    public int approveWrongHeat(long raceId, String transponderNumber, long userId) {
        WrongHeatDto flag = openFlag(raceId, transponderNumber);
        if (flag.status() == WrongHeatDto.Status.APPROVED) {
            return 0;
        }
        List<RaceEntry> inRace = raceEntryRepository.findByRaceIdOrderByGridPosition(raceId);
        if (inRace.stream().noneMatch(re -> Objects.equals(re.getEntryId(), flag.entryId()))) {
            boolean hasGrid = inRace.stream().anyMatch(re -> re.getGridPosition() != null);
            RaceEntry added = new RaceEntry();
            added.setRaceId(raceId);
            added.setEntryId(flag.entryId());
            added.setCarNumber(carNumberFor(flag, inRace));
            added.setGridPosition(hasGrid ? inRace.stream().map(RaceEntry::getGridPosition)
                    .filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0) + 1 : null);
            raceEntryRepository.save(added);
        }
        AfterCommit.run(() -> lapTimingService.addLateEntry(raceId, flag.entryId()));
        int lapsCredited = link(raceId, transponderNumber, flag.entryId(), userId);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("transponderNumber", transponderNumber);
        after.put("entryId", flag.entryId());
        after.put("lapsCredited", lapsCredited);
        audit.entry(Actor.official(userId), "WRONG_HEAT_APPROVED")
                .entity("race", raceId).race(raceId).event(labels.eventOf(raceId))
                .summary("Approved " + labels.driver(flag.entryId()) + " in " + labels.race(raceId)
                        + ", whose own heat is " + flag.heat() + ", crediting " + lapsCredited + " laps")
                .after(after).record();
        AfterCommit.run(() -> lapTimingService.decideWrongHeat(raceId, transponderNumber,
                WrongHeatDto.Status.APPROVED));
        return lapsCredited;
    }

    /**
     * Refuses a driver seen in the wrong heat: the flag stays on the screens as dealt with and the passings stay out
     * of the results. If the same number keeps passing it is not raised again. Nothing changes if it was disapproved
     * already.
     *
     * @throws StateConflictException if the race is not running or stopped, there is no such flag, or it has been
     *                                approved, since its laps are counted
     */
    public void disapproveWrongHeat(long raceId, String transponderNumber, long userId) {
        WrongHeatDto flag = openFlag(raceId, transponderNumber);
        if (flag.status() == WrongHeatDto.Status.DISAPPROVED) {
            return;
        }
        if (flag.status() == WrongHeatDto.Status.APPROVED) {
            throw new StateConflictException("That driver has been approved, and their laps are counted");
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("transponderNumber", transponderNumber);
        after.put("entryId", flag.entryId());
        audit.entry(Actor.official(userId), "WRONG_HEAT_DISAPPROVED")
                .entity("race", raceId).race(raceId).event(labels.eventOf(raceId))
                .summary("Disapproved " + labels.driver(flag.entryId()) + " in " + labels.race(raceId)
                        + ", whose own heat is " + flag.heat() + "; their passings do not count")
                .after(after).record();
        AfterCommit.run(() -> lapTimingService.decideWrongHeat(raceId, transponderNumber,
                WrongHeatDto.Status.DISAPPROVED));
    }

    /** The flag for the number in a race that is running or stopped. */
    private WrongHeatDto openFlag(long raceId, String transponderNumber) {
        Race race = raceRepository.getOrThrow(raceId);
        if (race.getStatus() != RaceStatus.RUNNING && race.getStatus() != RaceStatus.STOPPED) {
            throw new StateConflictException("A wrong-heat driver can only be decided while the race is running or stopped");
        }
        return lapTimingService.wrongHeatOf(raceId, transponderNumber)
                .orElseThrow(() -> new StateConflictException("Transponder " + transponderNumber
                        + " has not been flagged as a driver in the wrong heat"));
    }

    /** The driver's own car number if no one in this race has it, otherwise the next free one. */
    private static Integer carNumberFor(WrongHeatDto flag, List<RaceEntry> inRace) {
        Integer own = flag.carNumber();
        if (own != null && inRace.stream().noneMatch(re -> own.equals(re.getCarNumber()))) {
            return own;
        }
        return inRace.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    /**
     * Keeps the linked number on the entry, through a transponder swap so it is audited the same way. It goes in
     * the primary slot when the primary has not passed in this race (and there is no secondary, or the secondary
     * has passed), otherwise in the secondary slot when that is empty or has not passed. If both have passed it
     * goes in the first empty third or fourth slot (#482); if none is free nothing is saved, since replacing a
     * number that has passed would stop it counting. A number another competitor's entry holds is only linked
     * for this race, as a swap would refuse it.
     */
    private void saveOnEntry(long raceId, Long eventId, String transponderNumber, long entryId, long userId) {
        Entry entry = entryRepository.findById(entryId).orElse(null);
        if (eventId == null || entry == null || entry.hasTransponder(transponderNumber)) {
            return;
        }
        String primary = entry.getTransponderNumberSnapshot();
        String secondary = entry.getSecondaryTransponderNumber();
        boolean hasSecondary = secondary != null && !secondary.isBlank();
        boolean primarySeen = primary != null && lapTimingService.countPassingsForTransponder(raceId, primary) > 0;
        boolean secondarySeen = hasSecondary && lapTimingService.countPassingsForTransponder(raceId, secondary) > 0;
        TransponderSlot slot;
        if (!primarySeen && (!hasSecondary || secondarySeen)) {
            slot = TransponderSlot.PRIMARY;
        } else if (!hasSecondary || !secondarySeen) {
            slot = TransponderSlot.SECONDARY;
        } else if (isBlank(entry.getThirdTransponderNumber())) {
            slot = TransponderSlot.THIRD;
        } else if (isBlank(entry.getFourthTransponderNumber())) {
            slot = TransponderSlot.FOURTH;
        } else {
            log.info("Transponder {} linked to entry {} for race {} only: all its numbers are in use",
                    transponderNumber, entryId, raceId);
            return;
        }
        SwapResult result = swapService.swap(eventId, entryId, slot, transponderNumber, userId);
        if (!(result instanceof SwapResult.Success)) {
            log.info("Transponder {} linked to entry {} for race {} only: not saved on the entry ({})",
                    transponderNumber, entryId, raceId, result.getClass().getSimpleName());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
