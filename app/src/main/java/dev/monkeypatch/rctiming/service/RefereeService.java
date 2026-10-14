package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.race.IncidentReport;
import dev.monkeypatch.rctiming.domain.race.IncidentReportRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsence;
import dev.monkeypatch.rctiming.domain.race.MarshalAbsenceRepository;
import dev.monkeypatch.rctiming.domain.race.MarshalPenalty;
import dev.monkeypatch.rctiming.domain.race.MarshalPenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.Penalty;
import dev.monkeypatch.rctiming.domain.race.PenaltyRepository;
import dev.monkeypatch.rctiming.domain.race.PenaltyType;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.resultsexport.FinishedRaceCorrected;
import dev.monkeypatch.rctiming.timing.LapTimingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public RefereeService(IncidentReportRepository incidentReportRepository,
                          PenaltyRepository penaltyRepository,
                          MarshalAbsenceRepository marshalAbsenceRepository,
                          MarshalPenaltyRepository marshalPenaltyRepository,
                          RaceRepository raceRepository,
                          LapTimingService lapTimingService,
                          ApplicationEventPublisher eventPublisher,
                          AuditService audit,
                          RaceAuditLabels labels) {
        this.incidentReportRepository = incidentReportRepository;
        this.penaltyRepository = penaltyRepository;
        this.marshalAbsenceRepository = marshalAbsenceRepository;
        this.marshalPenaltyRepository = marshalPenaltyRepository;
        this.raceRepository = raceRepository;
        this.lapTimingService = lapTimingService;
        this.eventPublisher = eventPublisher;
        this.audit = audit;
        this.labels = labels;
    }

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
     * A LAP penalty comes off the car's laps in live timing straight away; a TIME penalty is only recorded, and is
     * added to the total time when the result is worked out. On a finished race the results are sent again.
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

        if (raceRepository.findById(raceId).map(r -> r.getStatus() == RaceStatus.FINISHED).orElse(false)) {
            // The race's results have gone out already; send them again with the penalty (#27)
            eventPublisher.publishEvent(new FinishedRaceCorrected(raceId));
        }
        if (type == PenaltyType.LAP) {
            lapTimingService.applyLapPenalty(raceId, entryId, value.intValueExact());
        }
        return saved;
    }

    /** Records that an entry missed their marshal duty. It gives no penalty: that is a separate action. */
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
     * Gives a marshal penalty for a recorded absence. The absence can be named (its id comes back from
     * recording it); otherwise the entry's most recent absence in the event is used, if there is one.
     */
    public MarshalPenalty applyMarshalPenalty(long raceId, long entryId, long eventId, Long absenceId, String notes,
                                              long userId) {
        MarshalPenalty penalty = new MarshalPenalty();
        penalty.setAbsenceId(absenceFor(entryId, eventId, absenceId));
        penalty.setEntryId(entryId);
        penalty.setEventId(eventId);
        penalty.setAppliedBy(userId);
        penalty.setAppliedAt(Instant.now());
        penalty.setNotes(notes);
        MarshalPenalty saved = marshalPenaltyRepository.save(penalty);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", saved.getEntryId());
        after.put("absenceId", saved.getAbsenceId());
        after.put("notes", saved.getNotes());
        audit.entry(Actor.official(userId), "MARSHAL_PENALTY_APPLIED").entity("marshal_penalty", saved.getId())
                .race(raceId).event(eventId)
                .summary("Gave " + labels.driver(entryId) + " a marshal penalty")
                .after(after).record();
        return saved;
    }

    /** The absence a marshal penalty is for: the one named, which must be this entry's in this event, or the latest. */
    private Long absenceFor(long entryId, long eventId, Long absenceId) {
        if (absenceId != null) {
            MarshalAbsence named = marshalAbsenceRepository.getOrThrow(absenceId);
            if (named.getEntryId() != entryId || named.getEventId() != eventId) {
                throw new StateConflictException("That absence was recorded for a different entry or event");
            }
            return named.getId();
        }
        return marshalAbsenceRepository.findByEventId(eventId).stream()
                .filter(a -> a.getEntryId() == entryId)
                .max(Comparator.comparing(MarshalAbsence::getId))
                .map(MarshalAbsence::getId)
                .orElse(null);
    }
}
