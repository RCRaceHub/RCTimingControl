package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.FinalLegsScope;
import dev.monkeypatch.rctiming.domain.format.BumpUpConfig;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.PointsFinalsConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatConfig;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates a class's finals from its qualifying results when an official asks for it from the finals line-up
 * panel. The layout comes from the class's format: a bump-up format's bump spots, otherwise no bumps, with the
 * official's cars per final or, when none is given, the format's grid size (else an even split of the field). The
 * number of finals is the number the generator made for the class; any an earlier calculation dropped as unused are
 * put back.
 *
 * <p>Nothing is calculated by itself when qualifying ends: the official reviews and edits the line-ups first
 * ({@link FinalsLineupService}), and calculating clears the event's confirmed line-up so the review screen comes back.
 * It refuses while a qualifying heat is still to run, when there are no finals, and
 * once a final has started, since calculating replaces the finals' grids.
 */
@Service
public class AutoFinalsSeedingService {

    private final RaceRepository raceRepository;
    private final EventClassRepository eventClassRepository;
    private final QualifyingStandingsService qualifyingStandingsService;
    private final BumpUpSeedingService bumpUpSeedingService;
    private final RaceFormatService raceFormatService;
    private final AuditService audit;
    private final EventRepository eventRepository;

    public AutoFinalsSeedingService(RaceRepository raceRepository,
                                    EventClassRepository eventClassRepository,
                                    QualifyingStandingsService qualifyingStandingsService,
                                    BumpUpSeedingService bumpUpSeedingService,
                                    RaceFormatService raceFormatService,
                                    AuditService audit,
                                    EventRepository eventRepository) {
        this.raceRepository = raceRepository;
        this.eventClassRepository = eventClassRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
        this.bumpUpSeedingService = bumpUpSeedingService;
        this.raceFormatService = raceFormatService;
        this.audit = audit;
        this.eventRepository = eventRepository;
    }

    /** Whether every qualifying heat is finished, as {@link FinalsLineupService} shows it. */
    boolean qualifyingComplete(Long eventClassId) {
        List<Race> qualifiers = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER);
        return qualifiers.stream().allMatch(q -> q.getStatus() == RaceStatus.FINISHED)
                && qualifiers.stream().anyMatch(q -> q.getAbandonedAt() == null);
    }

    /**
     * Calculates the class's finals from the qualifying results, on an official's say, the first time or again after
     * a result changed (a heat re-run or corrected), which also throws away any hand edits to the line-ups. Refused
     * until every qualifying heat is finished, since a driver who is only in an unfinished heat would be left out of
     * every final, and once a final has started.
     *
     * @param carsPerFinal cars on each final's grid, or null for the format's own grid size (an even split of the
     *                     field when the format has none); raised if the field would not fit
     * @throws EntityNotFoundException  if the class is not in the event
     * @throws IllegalArgumentException if {@code carsPerFinal} is less than 1
     * @throws StateConflictException   if qualifying is not complete, or the class has no finals
     */
    @Transactional
    public void reseed(Actor actor, Long eventId, Long eventClassId, Integer carsPerFinal) {
        if (carsPerFinal != null && carsPerFinal < 1) {
            throw new IllegalArgumentException("carsPerFinal must be at least 1");
        }
        EventClass eventClass = eventClassRepository.getOrThrow(eventClassId);
        if (!eventClass.getEventId().equals(eventId)) {
            throw new EntityNotFoundException("Event class " + eventClassId + " not found in event " + eventId);
        }
        if (!qualifyingComplete(eventClassId)) {
            throw new StateConflictException(
                "Every qualifying heat for this class must be finished, and not all abandoned, before its finals are seeded");
        }
        seed(actor, eventClassId, carsPerFinal);
    }

    /**
     * Calculates the finals of every class of the event that is ready, in one go, with the cars per final the official
     * set for the class, else its own default layout (the format's grid size, or an even split). A class is ready when it has finals, every qualifying heat is
     * finished (not all abandoned) and no final has started; any other class is left alone and named in the result,
     * so one class that is not ready never stops the rest. If a ready class then fails to calculate, the whole call is
     * rolled back and nothing changes. Like {@link #reseed}, it throws away hand edits to the
     * line-ups of the classes it calculates. Runs in one transaction on the request thread.
     *
     * @param carsPerFinal cars per final by event class id, null or missing for a class's default
     * @return the ids of the classes calculated and, for each class left alone, why
     * @throws EntityNotFoundException if the event has no classes
     */
    @Transactional
    public CalculateAllResult reseedAll(Actor actor, Long eventId, Map<Long, Integer> carsPerFinal) {
        Map<Long, Integer> cars = carsPerFinal == null ? Map.of() : carsPerFinal;
        if (cars.values().stream().anyMatch(c -> c == null || c < 1 || c > 100)) {
            throw new IllegalArgumentException("carsPerFinal must be 1 to 100");
        }
        List<EventClass> classes = eventClassRepository.findByEventId(eventId);
        if (classes.isEmpty()) {
            throw new EntityNotFoundException("Event " + eventId + " has no classes");
        }
        List<Long> calculated = new ArrayList<>();
        Map<Long, String> skipped = new LinkedHashMap<>();
        for (EventClass eventClass : classes) {
            Long classId = eventClass.getId();
            List<Race> finals = raceRepository.findByEventClassIdAndRoundType(classId, RoundType.FINAL);
            if (finals.isEmpty()) {
                skipped.put(classId, "No finals to calculate");
            } else if (finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING)) {
                skipped.put(classId, "A final has already started");
            } else if (raceRepository.findByEventClassIdAndRoundType(classId, RoundType.QUALIFIER).stream()
                    .allMatch(q -> q.getStatus() == RaceStatus.FINISHED && q.getAbandonedAt() != null)
                    && qualifyingRaceCount(classId) > 0) {
                skipped.put(classId, "Every qualifying heat was abandoned");
            } else if (!qualifyingComplete(classId)) {
                skipped.put(classId, "Qualifying is not finished");
            } else {
                seed(actor, classId, cars.get(classId));
                calculated.add(classId);
            }
        }
        return new CalculateAllResult(calculated, skipped);
    }

    private int qualifyingRaceCount(Long eventClassId) {
        return raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.QUALIFIER).size();
    }

    /**
     * What {@link #reseedAll} did.
     *
     * @param calculated ids of the event classes whose finals were calculated
     * @param skipped    event class id to the reason that class was left alone
     */
    public record CalculateAllResult(List<Long> calculated, Map<Long, String> skipped) {
    }

    private void seed(Actor actor, Long eventClassId, Integer carsPerFinal) {
        List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL);
        if (finals.isEmpty()) {
            throw new StateConflictException("This class has no finals to seed; generate the rounds first");
        }
        List<Long> standings = qualifyingStandingsService.standingsFor(eventClassId);
        EventClass eventClass = eventClassRepository.getOrThrow(eventClassId);
        Event event = eventRepository.getOrThrow(eventClass.getEventId());
        // The zombie round is not one of the A, B, C mains the field is split over
        int existing = (int) finals.stream()
                .filter(f -> !BumpUpSeedingService.ZOMBIE_LETTER.equals(f.getFinalLetter()))
                .filter(f -> !FinalLegs.isExtraLeg(f)).count();
        // Finals an earlier calculation dropped as unused come back, so fewer cars per final can use them again
        int finalsCount = eventClass.getFinalsCount() != null && eventClass.getFinalsCount() > existing
                ? bumpUpSeedingService.restoreFinals(eventClassId, eventClass.getFinalsCount(),
                        raceFormatService.startType(eventClass, RoundType.FINAL),
                        event.getFinalLegsScope() == FinalLegsScope.ALL ? raceFormatService.finalLegs(eventClass) : 1)
                : existing;
        int[] layout = layout(raceFormatService.getEffectiveConfig(eventClass), standings.size(), finalsCount, carsPerFinal);
        boolean fillTopFirst = raceFormatService.getEffectiveConfig(eventClass) instanceof PointsFinalsConfig points
                && points.fillTopFirst();
        bumpUpSeedingService.seedFinals(eventClassId, standings, finalsCount, layout[0], layout[1], fillTopFirst);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("finalsCount", finalsCount);
        after.put("carsPerFinal", layout[0]);
        after.put("bumpCount", layout[1]);
        after.put("qualifyingOrder", standings);
        if (event.getFinalsLineupConfirmedAt() != null) {
            event.setFinalsLineupConfirmedAt(null);
            eventRepository.save(event);
        }
        audit.entry(actor, "FINALS_SEEDED").entity("event_class", eventClassId)
                .event(eventClass.getEventId())
                .summary("Calculated the finals from " + standings.size() + " qualifiers ("
                        + finalsCount + " finals, " + layout[0] + " cars each, " + layout[1] + " bumping up)")
                .after(after).record();
    }

    /**
     * {cars per final, bump count} for a field of {@code field} drivers over {@code finalsCount} finals.
     * A bump-up format, or a Shikabane format with mains, keeps its bump spots; its grid size is used unless
     * {@code cars} says otherwise. Either way the grid grows rather than leave a driver out of every final. A
     * Points Finals format uses its own cars per final when it sets one.
     *
     * @param cars the official's cars per final, or null for the format's own
     */
    static int[] layout(RaceFormatConfig config, int field, int finalsCount, Integer cars) {
        if (config instanceof BumpUpConfig bumpUp && bumpUp.gridSize() > 0) {
            return fitted(cars != null ? cars : bumpUp.gridSize(), bumpUp.bumpSpots(), field, finalsCount);
        }
        if (config instanceof ShikabaneConfig shikabane && shikabane.mainGridSize() > 0) {
            // The mains work like a bump-up format's finals: promotion fills places at the back of the main above
            return fitted(cars != null ? cars : shikabane.mainGridSize(), shikabane.promotionSpots(), field,
                    finalsCount);
        }
        int even = Math.max(1, (field + finalsCount - 1) / finalsCount);
        // A Points Finals format may fix the cars per final; the grid still grows rather than leave a driver out
        int configured = config instanceof PointsFinalsConfig points && points.carsPerFinal() > 0
                ? points.carsPerFinal() : even;
        return new int[] {Math.max(cars != null ? cars : configured, even), 0};
    }

    /**
     * {cars per final, bump count} for a configured grid size and bump spots. The lowest final takes a full grid and
     * each higher one its grid less the bump spots, so the grid grows until the field fits rather than leave drivers
     * out of every final.
     */
    private static int[] fitted(int gridSize, int bumpSpots, int field, int finalsCount) {
        int cars = gridSize;
        int bump = Math.max(0, Math.min(bumpSpots, cars - 1));
        while (cars + (finalsCount - 1) * (cars - bump) < field) {
            cars++;
        }
        return new int[] {Math.max(1, cars), bump};
    }
}
