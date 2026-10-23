package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.race.IncidentReport;
import dev.monkeypatch.rctiming.domain.race.IncidentReportRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsence;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsenceRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalPenalty;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyKind;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Penalty;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.persistence.AfterCommit;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import dev.monkeypatch.rctiming.timing.dto.JumpStartDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Referee actions (OFFICIAL-03, OFFICIAL-04): incident reports, lap and time penalties, and marshal absences
 * and their penalties. Each is saved with an audit row, in the same transaction.
 */
@Service
@Transactional
public class RefereeService {

    /** More laps than any club race runs: a bigger LAP penalty is a typing mistake. */
    static final BigDecimal MAX_LAP_PENALTY = BigDecimal.valueOf(100);

    private final IncidentReportRepository incidentReportRepository;
    private final PenaltyRepository penaltyRepository;
    private final MarshalAbsenceRepository marshalAbsenceRepository;
    private final MarshalPenaltyRepository marshalPenaltyRepository;
    private final RaceRepository raceRepository;
    private final LapTimingService lapTimingService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService audit;
    private final RaceAuditLabels labels;
    private final FinalsMarshalPenalties finalsMarshalPenalties;
    private final EntryRepository entryRepository;

    public RefereeService(IncidentReportRepository incidentReportRepository,
                          PenaltyRepository penaltyRepository,
                          MarshalAbsenceRepository marshalAbsenceRepository,
                          MarshalPenaltyRepository marshalPenaltyRepository,
                          RaceRepository raceRepository,
                          LapTimingService lapTimingService,
                          ApplicationEventPublisher eventPublisher,
                          AuditService audit,
                          RaceAuditLabels labels,
                          FinalsMarshalPenalties finalsMarshalPenalties,
                          EntryRepository entryRepository) {
        this.incidentReportRepository = incidentReportRepository;
        this.penaltyRepository = penaltyRepository;
        this.marshalAbsenceRepository = marshalAbsenceRepository;
        this.marshalPenaltyRepository = marshalPenaltyRepository;
        this.raceRepository = raceRepository;
        this.lapTimingService = lapTimingService;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.labels = labels;
        this.finalsMarshalPenalties = finalsMarshalPenalties;
        this.entryRepository = entryRepository;
    }

    /** Records an incident report against an entry in a race. It changes nothing in the result; penalties are separate. */
    public IncidentReport raiseIncident(long raceId, long entryId, String incidentType, String description,
                                        long userId) {
        IncidentReport report = new IncidentReport();
        report.setRaceId(raceId);
        report.setEntryId(entryId);
        report.setIncidentType(incidentType);
        report.setDescription(description);
        report.setRaisedBy(userId);
        report.setRaisedAt(Instant.now());
        IncidentReport saved = incidentReportRepository.save(report);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", saved.getEntryId());
        after.put("incidentType", saved.getIncidentType());
        after.put("description", saved.getDescription());
        audit.entry(Actor.official(userId), "INCIDENT_RAISED").entity("incident_report", saved.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Raised an incident (" + saved.getIncidentType() + ") against "
                        + labels.driver(saved.getEntryId()) + " in " + labels.race(raceId))
                .after(after).record();
        return saved;
    }

    /**
     * Records that the referee took no action on a car's jump start. The alert goes from the referee screens; the car
     * keeps its lozenge in the listing. Audited as {@code JUMP_START_DISMISSED}, saying how early the car left, so the
     * log shows the jump start happened and was let go. The referee screens are told once the change commits.
     *
     * @throws StateConflictException if the entry has no open jump start in a race in progress, which includes one
     *                                already dismissed or settled by a penalty (HTTP 409)
     */
    public void dismissJumpStart(long raceId, long entryId, long userId) {
        JumpStartDto jumpStart = lapTimingService.openJumpStart(raceId, entryId)
                .orElseThrow(() -> new StateConflictException("That entry has no open jump start in this race"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", entryId);
        after.put("earlyMs", jumpStart.earlyMs());
        audit.entry(Actor.official(userId), "JUMP_START_DISMISSED").entity("race_entry", entryId)
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Took no action on a jump start by " + labels.driver(entryId) + " in "
                        + labels.race(raceId) + " (left "
                        + String.format(Locale.ROOT, "%.1f", jumpStart.earlyMs() / 1000.0) + " s early)")
                .after(after).record();
        AfterCommit.run(() -> lapTimingService.dismissJumpStart(raceId, entryId));
    }

    /**
     * A LAP penalty comes off the car's laps in live timing straight away; a TIME penalty is added to the car's
     * total time in live timing of a race in progress (the same figure the result will show). On a finished race
     * the results are sent again.
     * Live timing is only told after the commit. An open jump start alert for the car is settled by it: dismissed, with
     * a {@code JUMP_START_PENALISED} audit row naming the penalty.
     *
     * @throws IllegalArgumentException for a LAP penalty that is not a whole number of laps up to
     *                                  {@link #MAX_LAP_PENALTY}
     */
    public Penalty applyPenalty(long raceId, long entryId, PenaltyType type, BigDecimal value, String reason,
                                long userId) {
        if (type == PenaltyType.LAP
                && (value.stripTrailingZeros().scale() > 0 || value.compareTo(MAX_LAP_PENALTY) > 0)) {
            throw new IllegalArgumentException(
                    "A LAP penalty must be a whole number of laps, at most " + MAX_LAP_PENALTY + ", got: " + value);
        }

        Penalty penalty = new Penalty();
        penalty.setRaceId(raceId);
        penalty.setEntryId(entryId);
        penalty.setPenaltyType(type);
        penalty.setValue(value);
        penalty.setReason(reason);
        penalty.setAppliedBy(userId);
        penalty.setAppliedAt(Instant.now());
        Penalty saved = penaltyRepository.save(penalty);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", saved.getEntryId());
        after.put("penaltyType", saved.getPenaltyType());
        after.put("value", saved.getValue());
        after.put("reason", saved.getReason());
        audit.entry(Actor.official(userId), "PENALTY_APPLIED").entity("penalty", saved.getId())
                .race(raceId).event(labels.eventOf(raceId))
                .summary("Gave " + labels.driver(entryId) + " a " + type + " penalty of "
                        + value.stripTrailingZeros().toPlainString() + " in " + labels.race(raceId))
                .after(after).record();
        // A penalty settles an open jump start for the car, whatever its reason: say so in the log (the reason is in
        // the row), then drop the alert
        lapTimingService.openJumpStart(raceId, entryId).ifPresent(jumpStart -> {
            Map<String, Object> settled = new LinkedHashMap<>();
            settled.put("entryId", entryId);
            settled.put("penaltyId", saved.getId());
            settled.put("earlyMs", jumpStart.earlyMs());
            settled.put("reason", reason);
            audit.entry(Actor.official(userId), "JUMP_START_PENALISED").entity("race_entry", entryId)
                    .race(raceId).event(labels.eventOf(raceId))
                    .summary("Penalised the jump start by " + labels.driver(entryId) + " in " + labels.race(raceId)
                            + " (" + type + " penalty of " + value.stripTrailingZeros().toPlainString() + ")")
                    .after(settled).record();
        });
        AfterCommit.run(() -> lapTimingService.dismissJumpStart(raceId, entryId));

        RaceStatus status = raceRepository.findById(raceId).map(Race::getStatus).orElse(null);
        if (status == RaceStatus.FINISHED) {
            // The race's results have gone out already; send them again with the penalty (#27)
            eventPublisher.publishEvent(new FinishedRaceCorrected(raceId));
        }
        if (type == PenaltyType.LAP) {
            int laps = value.intValueExact();
            AfterCommit.run(() -> lapTimingService.applyLapPenalty(raceId, entryId, laps));
        } else if (status == RaceStatus.RUNNING || status == RaceStatus.STOPPED) {
            long penaltyMs = value.multiply(BigDecimal.valueOf(1000))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            AfterCommit.run(() -> lapTimingService.applyTimePenalty(raceId, entryId, penaltyMs));
        }
        return saved;
    }

    /**
     * Records that an entry missed their marshal duty. Marshals are assumed present, so this is the explicit way a miss
     * is recorded. It gives no penalty by itself: the referee can apply one afterwards, or skip this and apply the
     * penalty directly, which records the miss for them ({@link #applyMarshalPenalty}).
     */
    public MarshalAbsence recordMarshalAbsence(long raceId, long entryId, long eventId, long userId) {
        MarshalAbsence absence = new MarshalAbsence();
        absence.setRaceId(raceId);
        absence.setEntryId(entryId);
        absence.setEventId(eventId);
        absence.setRecordedBy(userId);
        absence.setRecordedAt(Instant.now());
        MarshalAbsence saved = marshalAbsenceRepository.save(absence);
        audit.entry(Actor.official(userId), "MARSHAL_ABSENCE_RECORDED").entity("marshal_absence", saved.getId())
                .race(raceId).event(eventId)
                .summary("Recorded that " + labels.driver(entryId) + " missed their marshal duty")
                .after(Map.of("entryId", entryId)).record();
        return saved;
    }

    /**
     * Gives a marshal penalty for a missed marshal duty. Marshals are assumed present, so the referee need not record
     * the miss first. The absence can be named (its id comes back from recording it); otherwise the entry's most
     * recent absence in the event that has no penalty yet is used, and when there is none the miss is recorded
     * here, against {@code raceId}, in the same transaction. The
     * {@code kind} says what it costs: the driver's best qualifying round (taken into account the next time the
     * qualifying order is worked out, so finals already seeded need seeding again) or a lap in their first final.
     *
     * @throws StateConflictException if the entry is not in the event, the named absence belongs to another entry or
     *                                event, or the absence already has a penalty
     */
    public MarshalPenalty applyMarshalPenalty(long raceId, long entryId, long eventId, MarshalPenaltyKind kind,
                                              Long absenceId, String notes, long userId) {
        Entry entry = entryRepository.getOrThrow(entryId);
        if (!Long.valueOf(eventId).equals(entry.getEventId())) {
            throw new StateConflictException("That entry is not in the event the penalty was given for");
        }
        Long forAbsence = absenceId != null
                ? namedAbsence(entryId, eventId, absenceId)
                : unpenalisedAbsence(entryId, eventId)
                        .orElseGet(() -> recordMissFor(raceId, entryId, eventId, userId));
        if (marshalPenaltyRepository.existsByAbsenceId(forAbsence)) {
            throw new StateConflictException("That missed marshal duty has a penalty already");
        }
        MarshalPenalty penalty = new MarshalPenalty();
        penalty.setAbsenceId(forAbsence);
        penalty.setEntryId(entryId);
        penalty.setEventId(eventId);
        penalty.setAppliedBy(userId);
        penalty.setAppliedAt(Instant.now());
        penalty.setNotes(notes);
        penalty.setKind(kind);
        MarshalPenalty saved = marshalPenaltyRepository.save(penalty);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", saved.getEntryId());
        after.put("absenceId", saved.getAbsenceId());
        after.put("kind", saved.getKind());
        after.put("notes", saved.getNotes());
        audit.entry(Actor.official(userId), "MARSHAL_PENALTY_APPLIED").entity("marshal_penalty", saved.getId())
                .race(raceId).event(eventId)
                .summary("Gave " + labels.driver(entryId) + " a marshal penalty ("
                        + (kind == MarshalPenaltyKind.FINALS_LAP ? "lap in the finals" : "best qualifying round")
                        + ")")
                .after(after).record();
        if (kind == MarshalPenaltyKind.FINALS_LAP) {
            // A final that has finished already has its stored result worked out again, and sent again (#27)
            finalsMarshalPenalties.firstFinalOf(entryId, entry.getEventClassId())
                    .filter(f -> f.getStatus() == RaceStatus.FINISHED)
                    .ifPresent(f -> eventPublisher.publishEvent(new FinishedRaceCorrected(f.getId())));
        }
        return saved;
    }

    /** The absence a marshal penalty is for, when the request names it: it must be this entry's in this event. */
    private Long namedAbsence(long entryId, long eventId, long absenceId) {
        MarshalAbsence named = marshalAbsenceRepository.getOrThrow(absenceId);
        if (named.getEntryId() != entryId || named.getEventId() != eventId) {
            throw new StateConflictException("That absence was recorded for a different entry or event");
        }
        return named.getId();
    }

    /**
     * Records the miss a penalty is for when none is waiting. An entry can miss a given race once (one absence per
     * race and entry), so a driver already penalised for missing {@code raceId} is refused, not penalised twice.
     */
    private long recordMissFor(long raceId, long entryId, long eventId, long userId) {
        boolean missedThisRace = marshalAbsenceRepository.findByEventId(eventId).stream()
                .anyMatch(a -> a.getEntryId() == entryId && a.getRaceId() == raceId);
        if (missedThisRace) {
            throw new StateConflictException("That missed marshal duty has a penalty already");
        }
        return recordMarshalAbsence(raceId, entryId, eventId, userId).getId();
    }

    /** The entry's latest absence in the event that has no penalty yet, if any. */
    private Optional<Long> unpenalisedAbsence(long entryId, long eventId) {
        return marshalAbsenceRepository.findByEventId(eventId).stream()
                .filter(a -> a.getEntryId() == entryId)
                .filter(a -> !marshalPenaltyRepository.existsByAbsenceId(a.getId()))
                .max(Comparator.comparing(MarshalAbsence::getId))
                .map(MarshalAbsence::getId);
    }
}
