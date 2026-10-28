package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClass;
import dev.monkeypatch.rctiming.domain.raceclass.RacingClassRepository;
import dev.monkeypatch.rctiming.persistence.ReadTransaction;
import dev.monkeypatch.rctiming.query.racecontrol.FinalsLineupDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalsReviewDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The finals line-up panel's reads and hand edits: who is on each final's grid, moving a driver to another final,
 * and swapping two drivers. Calculating the finals is {@link AutoFinalsSeedingService#reseed}; this service lets the
 * official check and adjust the result before the finals run. An edit is allowed only while both finals are still
 * PENDING, the same lock as everywhere else (a final is locked once it is called to the grid), and each runs in one
 * transaction with its audit row on the request thread. Moving does not have to keep the finals the same size.
 *
 * <p>It also holds the confirmation step of the line-up screen: once qualifying has ended the cockpit gives way to
 * that screen ({@link #review}) until race control confirms the line-ups ({@link #confirm}).
 */
@Service
@Transactional
public class FinalsLineupService {

    private final EventClassRepository eventClassRepository;
    private final RacingClassRepository racingClassRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final CompetitorRepository competitorRepository;
    private final QualifyingStandingsService qualifyingStandingsService;
    private final AutoFinalsSeedingService autoFinalsSeedingService;
    private final AuditService audit;
    private final EventRepository eventRepository;

    public FinalsLineupService(EventClassRepository eventClassRepository,
                               RacingClassRepository racingClassRepository,
                               RaceRepository raceRepository,
                               RaceEntryRepository raceEntryRepository,
                               EntryRepository entryRepository,
                               CompetitorRepository competitorRepository,
                               QualifyingStandingsService qualifyingStandingsService,
                               AutoFinalsSeedingService autoFinalsSeedingService,
                               AuditService audit,
                               EventRepository eventRepository) {
        this.eventClassRepository = eventClassRepository;
        this.racingClassRepository = racingClassRepository;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.competitorRepository = competitorRepository;
        this.qualifyingStandingsService = qualifyingStandingsService;
        this.autoFinalsSeedingService = autoFinalsSeedingService;
        this.audit = audit;
        this.eventRepository = eventRepository;
    }

    /** One line-up per class of the event that has finals, finals in letter order (A first). */
    @ReadTransaction
    public List<FinalsLineupDto> forEvent(Long eventId) {
        List<FinalsLineupDto> lineups = new ArrayList<>();
        for (EventClass eventClass : eventClassRepository.findByEventId(eventId)) {
            List<Race> finals = new ArrayList<>(
                    raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL));
            // Later legs of a final copy leg 1's line-up, so the panel shows each final once
            finals.removeIf(FinalLegs::isExtraLeg);
            if (finals.isEmpty()) {
                continue;
            }
            finals.sort(Comparator.comparing(Race::getFinalLetter, Comparator.nullsLast(Comparator.naturalOrder())));
            List<Race> qualifiers = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.QUALIFIER);
            List<Long> standings = qualifyingStandingsService.standingsFor(eventClass.getId());
            int defaultCars = qualifiers.stream()
                    .mapToInt(q -> raceEntryRepository.findByRaceIdOrderByGridPosition(q.getId()).size())
                    .max().orElse(0);
            List<FinalsLineupDto.Final> lineup = new ArrayList<>();
            boolean calculated = false;
            for (Race fin : finals) {
                List<FinalsLineupDto.Driver> drivers = raceEntryRepository.findByRaceIdOrderByGridPosition(fin.getId())
                        .stream().map(e -> {
                            int rank = standings.indexOf(e.getEntryId());
                            return new FinalsLineupDto.Driver(e.getEntryId(), driverName(e.getEntryId()),
                                    e.getGridPosition(), e.getCarNumber(), rank < 0 ? null : rank + 1);
                        }).toList();
                calculated |= !drivers.isEmpty();
                lineup.add(new FinalsLineupDto.Final(fin.getId(), fin.getFinalLetter(), fin.getStatus().name(),
                        fin.getBumpSlots(), fin.getStatus() == RaceStatus.PENDING, drivers));
            }
            lineups.add(new FinalsLineupDto(eventClass.getId(), className(eventClass),
                    autoFinalsSeedingService.qualifyingComplete(eventClass.getId()), calculated,
                    finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING), defaultCars, lineup));
        }
        return lineups;
    }

    /**
     * Whether the finals line-up screen must be shown for the event: true when it has finals, every qualifying heat
     * of every class with finals is finished, no final has left PENDING and the line-ups are unconfirmed. Read only.
     *
     * @throws EntityNotFoundException if there is no such event
     */
    @ReadTransaction
    public FinalsReviewDto review(Long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        boolean required = event.getFinalsLineupConfirmedAt() == null && hasCalculableFinals(eventId)
                && blocker(eventId, false) == null;
        return new FinalsReviewDto(required, event.getFinalsLineupConfirmedAt());
    }

    /**
     * Confirms the event's finals line-ups so the cockpit returns and the finals can be called. Refused until
     * qualifying is over, every class that can have its finals calculated has them, and while a final has started.
     * Confirming again keeps the first confirmation. Audited; runs in one transaction on the request thread.
     *
     * @throws EntityNotFoundException if there is no such event
     * @throws StateConflictException  if the event has no finals, qualifying is not over, a class is still to be
     *                                 calculated or a final has started
     */
    public FinalsReviewDto confirm(Actor actor, Long eventId) {
        Event event = eventRepository.getOrThrow(eventId);
        if (!hasCalculableFinals(eventId)) {
            throw new StateConflictException("This event has no finals that can be calculated, so there is nothing to confirm");
        }
        String reason = blocker(eventId, true);
        if (reason != null) {
            throw new StateConflictException(reason);
        }
        if (event.getFinalsLineupConfirmedAt() == null) {
            event.setFinalsLineupConfirmedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            eventRepository.save(event);
            audit.entry(actor, "FINALS_LINEUP_CONFIRMED").entity("event", eventId).event(eventId)
                    .summary("Confirmed the finals line-ups for " + event.getName()).record();
        }
        return new FinalsReviewDto(false, event.getFinalsLineupConfirmedAt());
    }

    /** Whether some class has finals and has finished qualifying with a heat that ran, so there is a line-up to confirm. */
    private boolean hasCalculableFinals(Long eventId) {
        return eventClassRepository.findByEventId(eventId).stream().anyMatch(
                c -> !raceRepository.findByEventClassIdAndRoundType(c.getId(), RoundType.FINAL).isEmpty()
                        && autoFinalsSeedingService.qualifyingComplete(c.getId()));
    }

    /**
     * Why the line-ups can't be reviewed or confirmed yet, or null when they can: an unfinished qualifying heat or a
     * started final always stops it; {@code needCalculated} also stops it while a class that has finished
     * qualifying has no drivers on its finals. A class whose qualifying heats were all abandoned has nothing to
     * calculate and never holds it up.
     */
    private String blocker(Long eventId, boolean needCalculated) {
        for (EventClass eventClass : eventClassRepository.findByEventId(eventId)) {
            List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.FINAL);
            if (finals.isEmpty()) {
                continue;
            }
            String name = className(eventClass);
            if (finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING)) {
                return "A final has already been called or started, so the line-ups are locked";
            }
            List<Race> qualifiers = raceRepository.findByEventClassIdAndRoundType(eventClass.getId(), RoundType.QUALIFIER);
            if (qualifiers.stream().anyMatch(q -> q.getStatus() != RaceStatus.FINISHED)) {
                return "Qualifying is not finished for " + name;
            }
            if (needCalculated && autoFinalsSeedingService.qualifyingComplete(eventClass.getId())
                    && finals.stream().filter(f -> !FinalLegs.isExtraLeg(f)).allMatch(f ->
                    raceEntryRepository.findByRaceIdOrderByGridPosition(f.getId()).isEmpty())) {
                return "Calculate the finals for " + name + " first";
            }
        }
        return null;
    }

    /**
     * Moves a driver out of one final into another of the same class, at {@code targetPosition} on the grid (the back
     * when null), closing the gap behind them. The finals need not stay the same size.
     *
     * @throws EntityNotFoundException  if the driver is not in the final
     * @throws IllegalArgumentException if the finals are the same or in different classes, or the move would leave a
     *                                  final with no drivers
     * @throws StateConflictException   if either final has been called or started
     */
    public void moveDriver(Actor actor, Long raceId, Long entryId, Long targetRaceId, Integer targetPosition) {
        Race source = raceRepository.getOrThrow(raceId);
        Race target = raceRepository.getOrThrow(targetRaceId);
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Pick a different final to move the driver to");
        }
        requireEditableFinals(source, target);
        RaceEntry moving = raceEntryRepository.findByRaceIdAndEntryId(source.getId(), entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + entryId + " is not in race " + raceId));
        if (raceEntryRepository.findByRaceIdAndEntryId(target.getId(), entryId).isPresent()) {
            throw new StateConflictException("The driver is already in final " + target.getFinalLetter());
        }
        if (moving.isBumped()) {
            throw new StateConflictException("That driver was bumped up into this final and can't be moved");
        }
        List<RaceEntry> rest = new ArrayList<>(raceEntryRepository.findByRaceIdOrderByGridPosition(source.getId()));
        rest.removeIf(e -> e.getEntryId().equals(entryId));
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("That is the only driver in final " + source.getFinalLetter()
                    + "; recalculate the finals instead of emptying it");
        }
        List<RaceEntry> joined = new ArrayList<>(raceEntryRepository.findByRaceIdOrderByGridPosition(target.getId()));
        int index = targetPosition == null ? joined.size() : Math.max(0, Math.min(targetPosition - 1, joined.size()));
        moving.setRaceId(target.getId());
        joined.add(index, moving);
        renumber(rest);
        renumber(joined);
        raceEntryRepository.saveAll(rest);
        raceEntryRepository.saveAll(joined);
        FinalLegs.syncGrids(raceRepository, raceEntryRepository, source.getEventClassId());
        record(actor, "FINALS_DRIVER_MOVED", source, "Moved entry " + entryId + " from final "
                + source.getFinalLetter() + " to final " + target.getFinalLetter() + " (place " + (index + 1) + ")",
                Map.of("entryId", entryId, "fromRaceId", source.getId(), "toRaceId", target.getId(),
                        "gridPosition", index + 1));
    }

    /**
     * Swaps two drivers' places: on one final's grid this reorders it, across two finals it exchanges the drivers.
     *
     * @throws EntityNotFoundException  if either driver is not in their final
     * @throws IllegalArgumentException if the finals are in different classes or it is the same driver
     * @throws StateConflictException   if either final has been called or started
     */
    public void swapDrivers(Actor actor, Long raceId, Long entryId, Long otherRaceId, Long otherEntryId) {
        Race first = raceRepository.getOrThrow(raceId);
        Race second = raceRepository.getOrThrow(otherRaceId);
        if (first.getId().equals(second.getId()) && entryId.equals(otherEntryId)) {
            throw new IllegalArgumentException("Pick two different drivers to swap");
        }
        requireEditableFinals(first, second);
        RaceEntry a = raceEntryRepository.findByRaceIdAndEntryId(first.getId(), entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + entryId + " is not in race " + raceId));
        RaceEntry b = raceEntryRepository.findByRaceIdAndEntryId(second.getId(), otherEntryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + otherEntryId + " is not in race " + otherRaceId));
        if (a.isBumped() || b.isBumped()) {
            throw new StateConflictException("A driver bumped up into a final can't be swapped");
        }
        if (!first.getId().equals(second.getId())
                && (raceEntryRepository.findByRaceIdAndEntryId(second.getId(), entryId).isPresent()
                || raceEntryRepository.findByRaceIdAndEntryId(first.getId(), otherEntryId).isPresent())) {
            throw new StateConflictException("One of the drivers is already in the other final");
        }
        Integer grid = a.getGridPosition();
        a.setGridPosition(b.getGridPosition());
        b.setGridPosition(grid);
        a.setRaceId(second.getId());
        b.setRaceId(first.getId());
        raceEntryRepository.save(a);
        raceEntryRepository.save(b);
        FinalLegs.syncGrids(raceRepository, raceEntryRepository, first.getEventClassId());
        record(actor, "FINALS_DRIVERS_SWAPPED", first, "Swapped entry " + entryId + " (final "
                + first.getFinalLetter() + ") with entry " + otherEntryId + " (final " + second.getFinalLetter() + ")",
                Map.of("entryId", entryId, "otherEntryId", otherEntryId, "raceId", first.getId(),
                        "otherRaceId", second.getId()));
    }

    private void requireEditableFinals(Race first, Race second) {
        if (first.getFinalLetter() == null || second.getFinalLetter() == null) {
            throw new IllegalArgumentException("Only drivers in finals can be edited here");
        }
        if (BumpUpSeedingService.ZOMBIE_LETTER.equals(first.getFinalLetter())
                || BumpUpSeedingService.ZOMBIE_LETTER.equals(second.getFinalLetter())) {
            throw new IllegalArgumentException("The zombie round fills itself once the mains are done");
        }
        if (FinalLegs.isExtraLeg(first) || FinalLegs.isExtraLeg(second)) {
            throw new IllegalArgumentException("Later legs copy leg 1's line-up; edit the final's first leg");
        }
        if (!Objects.equals(first.getEventClassId(), second.getEventClassId())) {
            throw new IllegalArgumentException("A driver can only move between finals of the same class");
        }
        for (Race race : List.of(first, second)) {
            if (race.getStatus() != RaceStatus.PENDING) {
                throw new StateConflictException("Final " + race.getFinalLetter()
                        + " has been called or started, so its line-up can't be changed");
            }
        }
    }

    private static void renumber(List<RaceEntry> entries) {
        int grid = 1;
        for (RaceEntry entry : entries) {
            entry.setGridPosition(grid++);
        }
    }

    private void record(Actor actor, String action, Race race, String summary, Map<String, Object> after) {
        Long eventId = eventClassRepository.getOrThrow(race.getEventClassId()).getEventId();
        audit.entry(actor, action).entity("race", race.getId()).race(race.getId()).event(eventId)
                .summary(summary).after(new LinkedHashMap<>(after)).record();
    }

    private String className(EventClass eventClass) {
        return racingClassRepository.findById(eventClass.getRacingClassId())
                .map(RacingClass::getName).orElse("Class " + eventClass.getRacingClassId());
    }

    private String driverName(Long entryId) {
        return entryRepository.findById(entryId)
                .flatMap(e -> competitorRepository.findById(e.getCompetitorId()))
                .map(c -> c.getDisplayName()).orElse("Entry " + entryId);
    }
}
