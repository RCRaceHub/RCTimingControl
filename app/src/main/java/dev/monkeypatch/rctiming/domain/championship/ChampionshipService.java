package dev.monkeypatch.rctiming.domain.championship;

import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.user.User;
import dev.monkeypatch.rctiming.domain.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ChampionshipService {

    private final ChampionshipRepository championshipRepository;
    private final ChampionshipClassRepository classRepository;
    private final ChampionshipEventLinkRepository eventLinkRepository;
    private final ChampionshipPointsScaleRepository pointsScaleRepository;
    private final ChampionshipExclusionRepository exclusionRepository;
    private final EventRepository eventRepository;
    private final RacingClassRepository racingClassRepository;
    private final CompetitorRepository competitorRepository;
    private final UserRepository userRepository;
    private final AuditService audit;

    public ChampionshipService(ChampionshipRepository championshipRepository,
                               ChampionshipClassRepository classRepository,
                               ChampionshipEventLinkRepository eventLinkRepository,
                               ChampionshipPointsScaleRepository pointsScaleRepository,
                               ChampionshipExclusionRepository exclusionRepository,
                               EventRepository eventRepository,
                               RacingClassRepository racingClassRepository,
                               CompetitorRepository competitorRepository,
                               UserRepository userRepository,
                               AuditService audit) {
        this.championshipRepository = championshipRepository;
        this.classRepository = classRepository;
        this.eventLinkRepository = eventLinkRepository;
        this.pointsScaleRepository = pointsScaleRepository;
        this.exclusionRepository = exclusionRepository;
        this.eventRepository = eventRepository;
        this.racingClassRepository = racingClassRepository;
        this.competitorRepository = competitorRepository;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    /** A championship's own settings, as an official sets them on create or update. */
    public record Settings(String name, Integer bestXFromYX, Integer bestXFromYY, ScoringSource scoringSource,
                           int tqBonusPoints, int afinalWinnerBonusPoints) {}

    /** A championship with its classes, rounds in order and points scale by position. */
    public record Detail(Championship championship, List<ChampionshipClass> classes,
                         List<ChampionshipEventLink> events, List<ChampionshipPointsScaleEntry> pointsScale) {}

    /** The points one finishing position scores. */
    public record ScalePoint(Integer position, Integer points) {
        static ScalePoint of(ChampionshipPointsScaleEntry e) {
            return new ScalePoint(e.getPosition(), e.getPoints());
        }
    }

    /** An exclusion and the name of the official who recorded it, null if that account has been removed. */
    public record Exclusion(ChampionshipExclusion exclusion, String createdByName) {}

    public Championship create(Actor actor, Settings settings) {
        Championship c = new Championship();
        apply(c, settings);
        Championship saved = championshipRepository.save(c);
        audit.entry(actor, "CHAMPIONSHIP_CREATED").entity("championship", saved.getId())
                .summary("Created championship " + saved.getName())
                .after(settingsOf(saved)).record();
        return saved;
    }

    public Championship update(Actor actor, Long id, Settings settings) {
        Championship c = getChampionshipOrThrow(id);
        Map<String, Object> before = settingsOf(c);
        apply(c, settings);
        Championship saved = championshipRepository.save(c);
        audit.entry(actor, "CHAMPIONSHIP_UPDATED").entity("championship", id)
                .summary("Changed the settings of championship " + saved.getName())
                .before(before).after(settingsOf(saved)).record();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Championship> listAll() {
        return championshipRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Detail getDetail(Long id) {
        Championship c = getChampionshipOrThrow(id);
        return new Detail(c,
                classRepository.findByChampionshipId(id),
                eventLinkRepository.findByChampionshipIdOrderByRoundNumberAsc(id),
                pointsScaleRepository.findByChampionshipIdOrderByPositionAsc(id));
    }

    /** Adds a class; a null best-X-from-Y inherits the championship's. */
    public ChampionshipClass addClass(Actor actor, Long championshipId, Long racingClassId,
                                      Integer bestXFromYX, Integer bestXFromYY) {
        championshipRepository.requireExists(championshipId);
        racingClassRepository.requireExists(racingClassId);
        if (classRepository.existsByChampionshipIdAndRacingClassId(championshipId, racingClassId)) {
            throw new DataIntegrityViolationException(
                    "Racing class " + racingClassId + " already belongs to championship " + championshipId);
        }
        ChampionshipClass cc = new ChampionshipClass();
        cc.setChampionshipId(championshipId);
        cc.setRacingClassId(racingClassId);
        cc.setBestXFromYX(bestXFromYX);
        cc.setBestXFromYY(bestXFromYY);
        ChampionshipClass saved = classRepository.save(cc);
        audit.entry(actor, "CHAMPIONSHIP_CLASS_ADDED").entity("championship", championshipId)
                .summary("Added class " + className(saved.getRacingClassId()) + " to championship "
                        + championshipName(championshipId))
                .after(classValues(saved)).record();
        return saved;
    }

    public void removeClass(Actor actor, Long championshipId, Long racingClassId) {
        championshipRepository.requireExists(championshipId);
        // Nothing to record if the class was not in the championship
        classRepository.findByChampionshipId(championshipId).stream()
                .filter(c -> c.getRacingClassId().equals(racingClassId))
                .findFirst()
                .ifPresent(existing -> audit.entry(actor, "CHAMPIONSHIP_CLASS_REMOVED")
                        .entity("championship", championshipId)
                        .summary("Removed class " + className(racingClassId) + " from championship "
                                + championshipName(championshipId))
                        .before(classValues(existing)).record());
        classRepository.deleteByChampionshipIdAndRacingClassId(championshipId, racingClassId);
    }

    public ChampionshipEventLink linkEvent(Actor actor, Long championshipId, Long eventId, Integer roundNumber) {
        championshipRepository.requireExists(championshipId);
        eventRepository.requireExists(eventId);
        if (eventLinkRepository.existsByChampionshipIdAndEventId(championshipId, eventId)) {
            throw new DataIntegrityViolationException(
                    "Event " + eventId + " already linked to championship " + championshipId);
        }
        if (eventLinkRepository.existsByChampionshipIdAndRoundNumber(championshipId, roundNumber)) {
            throw new DataIntegrityViolationException(
                    "Round " + roundNumber + " already assigned in championship " + championshipId);
        }
        ChampionshipEventLink link = new ChampionshipEventLink();
        link.setChampionshipId(championshipId);
        link.setEventId(eventId);
        link.setRoundNumber(roundNumber);
        ChampionshipEventLink saved = eventLinkRepository.save(link);
        audit.entry(actor, "CHAMPIONSHIP_EVENT_LINKED").entity("championship", championshipId).event(saved.getEventId())
                .summary("Linked " + eventName(saved.getEventId()) + " to championship "
                        + championshipName(championshipId) + " as round " + saved.getRoundNumber())
                .after(linkValues(saved)).record();
        return saved;
    }

    public void unlinkEvent(Actor actor, Long championshipId, Long eventId) {
        championshipRepository.requireExists(championshipId);
        // Nothing to record if the event was not linked
        eventLinkRepository.findByChampionshipIdOrderByRoundNumberAsc(championshipId).stream()
                .filter(l -> l.getEventId().equals(eventId))
                .findFirst()
                .ifPresent(existing -> audit.entry(actor, "CHAMPIONSHIP_EVENT_UNLINKED")
                        .entity("championship", championshipId).event(eventId)
                        .summary("Unlinked " + eventName(eventId) + " from championship "
                                + championshipName(championshipId) + " (it was round " + existing.getRoundNumber() + ")")
                        .before(linkValues(existing)).record());
        eventLinkRepository.deleteByChampionshipIdAndEventId(championshipId, eventId);
    }

    /** CHAMP-04: replace-all points scale in a single transaction. */
    public List<ChampionshipPointsScaleEntry> replacePointsScale(Actor actor, Long championshipId,
                                                                 List<ScalePoint> scale) {
        championshipRepository.requireExists(championshipId);
        List<ScalePoint> before = pointsScaleRepository.findByChampionshipIdOrderByPositionAsc(championshipId)
                .stream().map(ScalePoint::of).toList();
        pointsScaleRepository.deleteAllByChampionshipId(championshipId);
        List<ChampionshipPointsScaleEntry> toSave = new ArrayList<>(scale.size());
        for (ScalePoint entry : scale) {
            toSave.add(new ChampionshipPointsScaleEntry(championshipId, entry.position(), entry.points()));
        }
        List<ChampionshipPointsScaleEntry> saved = pointsScaleRepository.saveAll(toSave).stream()
                .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .toList();
        audit.entry(actor, "CHAMPIONSHIP_POINTS_SCALE_CHANGED").entity("championship", championshipId)
                .summary("Changed the points scale of championship " + championshipName(championshipId))
                .before(before).after(saved.stream().map(ScalePoint::of).toList()).record();
        return saved;
    }

    /**
     * CHAMP-02 + CHAMP-09: exclude a driver (a competitor, L5) from one event and record an audit row.
     * The recording official comes from the authenticated {@code actor}, never from the request body.
     */
    public Exclusion createExclusion(Actor actor, Long championshipId, Long driverId, Long eventId, String reason) {
        championshipRepository.requireExists(championshipId);
        competitorRepository.requireExists(driverId);
        eventRepository.requireExists(eventId);
        if (exclusionRepository.existsByChampionshipIdAndDriverIdAndEventId(championshipId, driverId, eventId)) {
            throw new DataIntegrityViolationException(
                    "Driver " + driverId + " is already excluded from event " + eventId
                            + " in championship " + championshipId);
        }
        ChampionshipExclusion x = new ChampionshipExclusion();
        x.setChampionshipId(championshipId);
        x.setDriverId(driverId);
        x.setEventId(eventId);
        x.setReason(reason);
        x.setCreatedBy(actor.userId());
        ChampionshipExclusion saved = exclusionRepository.save(x);
        audit.entry(actor, "CHAMPIONSHIP_EXCLUSION_ADDED").entity("championship", championshipId).event(saved.getEventId())
                .summary("Excluded " + competitorName(saved.getDriverId()) + " from " + eventName(saved.getEventId())
                        + " in championship " + championshipName(championshipId) + ": " + saved.getReason())
                .after(exclusionValues(saved)).record();
        return new Exclusion(saved, officialName(actor.userId()));
    }

    public void deleteExclusion(Actor actor, Long championshipId, Long exclusionId) {
        ChampionshipExclusion x = exclusionRepository.getOrThrow(exclusionId);
        if (!x.getChampionshipId().equals(championshipId)) {
            throw new EntityNotFoundException(
                    "Exclusion " + exclusionId + " does not belong to championship " + championshipId);
        }
        // The row is deleted, so the audit row is where who excluded the driver, and why, survives
        audit.entry(actor, "CHAMPIONSHIP_EXCLUSION_REMOVED").entity("championship", championshipId).event(x.getEventId())
                .summary("Removed the exclusion of " + competitorName(x.getDriverId()) + " from "
                        + eventName(x.getEventId()) + " in championship " + championshipName(championshipId))
                .before(exclusionValues(x)).record();
        exclusionRepository.delete(x);
    }

    @Transactional(readOnly = true)
    public List<Exclusion> listExclusions(Long championshipId) {
        championshipRepository.requireExists(championshipId);
        List<ChampionshipExclusion> exclusions =
                exclusionRepository.findByChampionshipIdOrderByCreatedAtDesc(championshipId);
        Map<Long, String> officials = new HashMap<>();
        userRepository.findAllById(exclusions.stream().map(ChampionshipExclusion::getCreatedBy).distinct().toList())
                .forEach(u -> officials.put(u.getId(), fullName(u)));
        return exclusions.stream()
                .map(x -> new Exclusion(x, officials.get(x.getCreatedBy())))
                .toList();
    }

    private String officialName(Long userId) {
        return userRepository.findById(userId).map(ChampionshipService::fullName).orElse(null);
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static void apply(Championship c, Settings settings) {
        c.setName(settings.name());
        c.setBestXFromYX(settings.bestXFromYX());
        c.setBestXFromYY(settings.bestXFromYY());
        c.setScoringSource(settings.scoringSource());
        c.setTqBonusPoints(settings.tqBonusPoints());
        c.setAfinalWinnerBonusPoints(settings.afinalWinnerBonusPoints());
    }

    private static Map<String, Object> settingsOf(Championship c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("bestXFromYX", c.getBestXFromYX());
        m.put("bestXFromYY", c.getBestXFromYY());
        m.put("scoringSource", c.getScoringSource());
        m.put("tqBonusPoints", c.getTqBonusPoints());
        m.put("afinalWinnerBonusPoints", c.getAfinalWinnerBonusPoints());
        return m;
    }

    private Map<String, Object> classValues(ChampionshipClass c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("racingClassId", c.getRacingClassId());
        m.put("className", className(c.getRacingClassId()));
        m.put("bestXFromYX", c.getBestXFromYX());
        m.put("bestXFromYY", c.getBestXFromYY());
        return m;
    }

    private Map<String, Object> linkValues(ChampionshipEventLink l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", l.getEventId());
        m.put("eventName", eventName(l.getEventId()));
        m.put("roundNumber", l.getRoundNumber());
        return m;
    }

    /** Everything an exclusion says, including who recorded it and when, so that survives its deletion. */
    private Map<String, Object> exclusionValues(ChampionshipExclusion x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("driverId", x.getDriverId());
        m.put("driverName", competitorName(x.getDriverId()));
        m.put("eventId", x.getEventId());
        m.put("eventName", eventName(x.getEventId()));
        m.put("reason", x.getReason());
        m.put("recordedBy", x.getCreatedBy());
        m.put("recordedByName", officialName(x.getCreatedBy()));
        m.put("recordedAt", x.getCreatedAt());
        return m;
    }

    private String championshipName(Long id) {
        return championshipRepository.findById(id).map(Championship::getName).orElse("#" + id);
    }

    private String className(Long racingClassId) {
        return racingClassRepository.findById(racingClassId).map(c -> c.getName()).orElse("#" + racingClassId);
    }

    private String eventName(Long eventId) {
        return eventRepository.findById(eventId).map(e -> e.getName()).orElse("event #" + eventId);
    }

    private String competitorName(Long competitorId) {
        return competitorRepository.findById(competitorId).map(c -> c.getDisplayName()).orElse("competitor #" + competitorId);
    }

    private Championship getChampionshipOrThrow(Long id) {
        return championshipRepository.getOrThrow(id);
    }
}
