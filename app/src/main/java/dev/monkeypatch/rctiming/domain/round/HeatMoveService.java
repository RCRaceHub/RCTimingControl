package dev.monkeypatch.rctiming.domain.round;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Changes who is in the heats that have not been called yet: moves a driver from one heat to another, such as when a
 * withdrawal leaves the heats lopsided, and adds a late entry (a walk-in made after the run order was generated) to a
 * heat. Each change runs in one transaction with its audit row, on a request thread.
 */
@Service
@Transactional
public class HeatMoveService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RoundRepository roundRepository;
    private final EntryRepository entryRepository;
    private final AuditService audit;

    public HeatMoveService(RaceRepository raceRepository,
                           RaceEntryRepository raceEntryRepository,
                           RoundRepository roundRepository,
                           EntryRepository entryRepository,
                           AuditService audit) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.roundRepository = roundRepository;
        this.entryRepository = entryRepository;
        this.audit = audit;
    }

    /**
     * @param allRounds also move the driver between the same two heats in every other practice and qualifying round
     *                  where both are still pending; otherwise the two races must be in the same round
     * @throws IllegalArgumentException if the races are the same, in different classes or events, finals, or (without
     *                                  {@code allRounds}) in different rounds
     * @throws StateConflictException   if either race has been called or started
     */
    public void moveEntry(Actor actor, Long raceId, Long entryId, Long targetRaceId, boolean allRounds) {
        Race source = raceRepository.getOrThrow(raceId);
        Race target = raceRepository.getOrThrow(targetRaceId);
        Round sourceRound = roundRepository.getOrThrow(source.getRoundId());
        Round targetRound = roundRepository.getOrThrow(target.getRoundId());
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Pick a different heat to move the driver to");
        }
        if (source.getFinalLetter() != null || target.getFinalLetter() != null) {
            throw new IllegalArgumentException("Drivers are moved between heats, not into or out of a final");
        }
        if (!Objects.equals(source.getEventClassId(), target.getEventClassId())
                || !Objects.equals(sourceRound.getEventId(), targetRound.getEventId())) {
            throw new IllegalArgumentException("A driver can only move between heats of the same class");
        }
        if (!allRounds && !source.getRoundId().equals(target.getRoundId())) {
            throw new IllegalArgumentException("Both heats must be in the same round");
        }
        requirePending(source);
        requirePending(target);
        raceEntryRepository.findByRaceIdAndEntryId(source.getId(), entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + entryId + " is not in race " + raceId));
        if (raceEntryRepository.findByRaceIdOrderByGridPosition(source.getId()).size() == 1) {
            throw new IllegalArgumentException(
                    "That is the only driver in the heat; abandon the heat instead of emptying it");
        }

        List<Race[]> pairs = new ArrayList<>();
        pairs.add(new Race[] {source, target});
        if (allRounds) {
            for (Round round : roundRepository.findByEventIdOrderBySequenceInEvent(sourceRound.getEventId())) {
                if (round.getId().equals(source.getRoundId())) {
                    continue;
                }
                Race from = heatOf(round, source);
                Race to = heatOf(round, target);
                if (from != null && to != null && from.getStatus() == RaceStatus.PENDING
                        && to.getStatus() == RaceStatus.PENDING
                        && raceEntryRepository.findByRaceIdAndEntryId(from.getId(), entryId).isPresent()
                        && raceEntryRepository.findByRaceIdAndEntryId(to.getId(), entryId).isEmpty()
                        && raceEntryRepository.findByRaceIdOrderByGridPosition(from.getId()).size() > 1) {
                    pairs.add(new Race[] {from, to});
                }
            }
        }
        for (Race[] pair : pairs) {
            move(entryId, pair[0], pair[1]);
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", entryId);
        after.put("fromHeat", source.getHeatNumber());
        after.put("toHeat", target.getHeatNumber());
        after.put("raceIds", pairs.stream().map(p -> p[0].getId()).toList());
        audit.entry(actor, "ENTRY_MOVED_HEAT").entity("race", source.getId()).race(source.getId())
                .event(sourceRound.getEventId())
                .summary("Moved entry " + entryId + " from heat " + source.getHeatNumber() + " to heat "
                        + target.getHeatNumber() + " in " + pairs.size() + (pairs.size() == 1 ? " round" : " rounds"))
                .after(after).record();
    }

    /**
     * Puts an entry that is in no heat yet, such as a walk-in added after the run order was generated, at the back
     * of a heat that has not been called. With {@code allRounds} it is also added to the same heat number in every
     * later practice and qualifying round of its class where that heat is still pending and the entry is not
     * already in the class's heats; rounds that have run are skipped quietly, and so are earlier rounds.
     *
     * <p>The entry goes last on the grid (when the heat has grid positions) and takes the next free car number in
     * the heat, the same number in every round where that is free. Nobody else is renumbered. Records
     * {@code ENTRY_ADDED_TO_HEAT} in the audit log.
     *
     * @throws EntityNotFoundException  if the race or entry does not exist
     * @throws IllegalArgumentException if the race is a final, or the entry is not a confirmed entry of the race's
     *                                  class and event
     * @throws StateConflictException   if the heat has been called or started, or the entry is already in a heat
     *                                  of this round
     */
    public void addEntry(Actor actor, Long raceId, Long entryId, boolean allRounds) {
        Race target = raceRepository.getOrThrow(raceId);
        Round targetRound = roundRepository.getOrThrow(target.getRoundId());
        Entry entry = entryRepository.getOrThrow(entryId);
        if (target.getFinalLetter() != null) {
            throw new IllegalArgumentException("A driver is added to a heat, not to a final");
        }
        if (!Objects.equals(entry.getEventId(), targetRound.getEventId())
                || !Objects.equals(entry.getEventClassId(), target.getEventClassId())) {
            throw new IllegalArgumentException("A driver can only be added to a heat of their own class");
        }
        if (entry.getStatus() != EntryStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only a confirmed entry can be added to a heat");
        }
        requirePending(target);
        if (inRoundAlready(target, entryId)) {
            throw new StateConflictException("The driver is already in a heat of this round");
        }

        int car = add(entryId, target, null);
        List<Long> raceIds = new ArrayList<>();
        raceIds.add(target.getId());
        if (allRounds) {
            for (Round round : roundRepository.findByEventIdOrderBySequenceInEvent(targetRound.getEventId())) {
                if (round.getSequenceInEvent() <= targetRound.getSequenceInEvent()) {
                    continue;
                }
                Race to = heatOf(round, target);
                if (to != null && to.getStatus() == RaceStatus.PENDING && !inRoundAlready(to, entryId)) {
                    add(entryId, to, car);
                    raceIds.add(to.getId());
                }
            }
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("entryId", entryId);
        after.put("heat", target.getHeatNumber());
        after.put("carNumber", car);
        after.put("raceIds", raceIds);
        audit.entry(actor, "ENTRY_ADDED_TO_HEAT").entity("race", target.getId()).race(target.getId())
                .event(targetRound.getEventId())
                .summary("Added entry " + entryId + " to heat " + target.getHeatNumber() + " in " + raceIds.size()
                        + (raceIds.size() == 1 ? " round" : " rounds"))
                .after(after).record();
    }

    /** Whether the entry is already in one of the heats of the race's round that belong to the race's class. */
    private boolean inRoundAlready(Race race, Long entryId) {
        return raceRepository.findByRoundIdOrderBySequenceInRound(race.getRoundId()).stream()
                .filter(r -> Objects.equals(r.getEventClassId(), race.getEventClassId()))
                .anyMatch(r -> raceEntryRepository.findByRaceIdAndEntryId(r.getId(), entryId).isPresent());
    }

    /** Adds the entry at the back of the heat and returns its car number, the preferred one if the heat has not got it. */
    private int add(Long entryId, Race to, Integer preferredCar) {
        List<RaceEntry> inHeat = raceEntryRepository.findByRaceIdOrderByGridPosition(to.getId());
        int car = preferredCar != null && inHeat.stream().noneMatch(e -> preferredCar.equals(e.getCarNumber()))
                ? preferredCar
                : inHeat.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).max().orElse(0) + 1;
        boolean hasGrid = inHeat.stream().anyMatch(e -> e.getGridPosition() != null);
        RaceEntry added = new RaceEntry();
        added.setRaceId(to.getId());
        added.setEntryId(entryId);
        added.setCarNumber(car);
        added.setGridPosition(hasGrid
                ? inHeat.stream().map(RaceEntry::getGridPosition).filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).max().orElse(0) + 1
                : null);
        raceEntryRepository.save(added);
        return car;
    }

    private static void requirePending(Race race) {
        if (race.getStatus() != RaceStatus.PENDING) {
            throw new StateConflictException("Heat " + race.getHeatNumber()
                    + " has been called or started, so drivers can't be moved into or out of it");
        }
    }

    /** The race of the same class and heat number as {@code like} in the given round, if the round has one. */
    private Race heatOf(Round round, Race like) {
        return raceRepository.findByRoundIdOrderBySequenceInRound(round.getId()).stream()
                .filter(r -> r.getFinalLetter() == null
                        && Objects.equals(r.getEventClassId(), like.getEventClassId())
                        && r.getHeatNumber() == like.getHeatNumber())
                .findFirst().orElse(null);
    }

    /**
     * Takes the driver out of {@code from}, closing the gap in its grid, and adds them to the end of {@code to},
     * whose grid positions are then 1 to N again. Car numbers only change when the rounds or finals are calculated,
     * so the driver keeps theirs and nobody else is renumbered; only if the target heat already has that number does
     * the driver take the next free one there.
     */
    private void move(Long entryId, Race from, Race to) {
        RaceEntry moving = raceEntryRepository.findByRaceIdAndEntryId(from.getId(), entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + entryId + " is not in race " + from.getId()));
        if (raceEntryRepository.findByRaceIdAndEntryId(to.getId(), entryId).isPresent()) {
            throw new StateConflictException("The driver is already in heat " + to.getHeatNumber());
        }
        List<RaceEntry> target = raceEntryRepository.findByRaceIdOrderByGridPosition(to.getId());
        Integer kept = moving.getCarNumber();
        Integer car = kept;
        if (kept == null || target.stream().anyMatch(t -> kept.equals(t.getCarNumber()))) {
            car = target.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).max().orElse(0) + 1;
        }
        Integer nextGrid = moving.getGridPosition() == null ? null
                : target.stream().map(RaceEntry::getGridPosition).filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).max().orElse(0) + 1;
        moving.setRaceId(to.getId());
        moving.setGridPosition(nextGrid);
        moving.setCarNumber(car);
        raceEntryRepository.save(moving);

        List<RaceEntry> joined = new ArrayList<>(raceEntryRepository.findByRaceIdOrderByGridPosition(to.getId()));
        renumberGrid(joined);
        raceEntryRepository.saveAll(joined);

        List<RaceEntry> rest = new ArrayList<>(raceEntryRepository.findByRaceIdOrderByGridPosition(from.getId()));
        renumberGrid(rest);
        raceEntryRepository.saveAll(rest);
    }

    /** Makes the grid positions that are set run 1 to N in their present order, so a move leaves no gap or repeat. */
    private static void renumberGrid(List<RaceEntry> entries) {
        int grid = 1;
        for (RaceEntry entry : entries.stream().filter(e -> e.getGridPosition() != null)
                .sorted(Comparator.comparing(RaceEntry::getGridPosition)).toList()) {
            entry.setGridPosition(grid++);
        }
    }
}
