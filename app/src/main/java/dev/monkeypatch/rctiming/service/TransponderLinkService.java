package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAudit;
import dev.monkeypatch.rctiming.timing.UnknownTransponderLinkAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Links an unknown transponder to an entry during a live race (TIMING-08), crediting every passing it has
 * made since the start.
 */
@Service
@Transactional
public class TransponderLinkService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final LapTimingService lapTimingService;
    private final UnknownTransponderLinkAuditRepository linkAuditRepository;
    private final AuditService audit;
    private final RaceAuditLabels labels;

    public TransponderLinkService(RaceRepository raceRepository,
                                  RaceEntryRepository raceEntryRepository,
                                  LapTimingService lapTimingService,
                                  UnknownTransponderLinkAuditRepository linkAuditRepository,
                                  AuditService audit,
                                  RaceAuditLabels labels) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.lapTimingService = lapTimingService;
        this.linkAuditRepository = linkAuditRepository;
        this.audit = audit;
        this.labels = labels;
    }

    /**
     * Links the transponder to an entry in a running or stopped race and credits its passings to that entry.
     * Linking it again to the same entry credits nothing more; linking it to a different one is refused, since
     * its laps are already credited to the first.
     *
     * <p>The audit rows and the link are one transaction, with the link last, so a link that fails records
     * nothing. The live timing is changed before the commit (see #217).
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

        // Counted before linking, since linking credits them
        int lapsCredited = lapTimingService.countPassingsForTransponder(raceId, transponderNumber);

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

        lapTimingService.linkTransponder(raceId, transponderNumber, entryId);
        return lapsCredited;
    }
}
