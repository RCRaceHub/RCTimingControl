package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.EntityNotFoundException;
import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
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
 * Moves a driver from one heat to another before either has been called, such as when a late entry or a withdrawal
 * leaves the heats lopsided. The move runs in one transaction with its audit row.
 */
@Service
@Transactional
public class HeatMoveService {

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RoundRepository roundRepository;
    private final AuditService audit;

    public HeatMoveService(RaceRepository raceRepository,
                           RaceEntryRepository raceEntryRepository,
                           RoundRepository roundRepository,
                           AuditService audit) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.roundRepository = roundRepository;
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

        List<Race[]> pairs = new ArrayList<>();
        pairs.add(new Race[] {source, target});
        if (allRounds) {
            for (Round round : roundRepository.findByEventIdOrderBySequenceInEvent(sourceRound.getEventId())) {
                if (round.getId().equals(source.getRoundId())) continue;
                Race from = heatOf(round, source);
                Race to = heatOf(round, target);
                if (from != null && to != null && from.getStatus() == RaceStatus.PENDING
                        && to.getStatus() == RaceStatus.PENDING
                        && raceEntryRepository.findByRaceIdAndEntryId(from.getId(), entryId).isPresent()) {
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

    /** Takes the driver out of {@code from}, closing the gap in its grid and car numbers, and adds them to the end of {@code to}. */
    private void move(Long entryId, Race from, Race to) {
        RaceEntry moving = raceEntryRepository.findByRaceIdAndEntryId(from.getId(), entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entry " + entryId + " is not in race " + from.getId()));
        if (raceEntryRepository.findByRaceIdAndEntryId(to.getId(), entryId).isPresent()) {
            throw new StateConflictException("The driver is already in heat " + to.getHeatNumber());
        }
        List<RaceEntry> target = raceEntryRepository.findByRaceIdOrderByGridPosition(to.getId());
        int nextCar = target.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
        Integer nextGrid = moving.getGridPosition() == null ? null
                : target.stream().map(RaceEntry::getGridPosition).filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).max().orElse(0) + 1;
        moving.setRaceId(to.getId());
        moving.setGridPosition(nextGrid);
        moving.setCarNumber(nextCar);
        raceEntryRepository.save(moving);

        List<RaceEntry> rest = raceEntryRepository.findByRaceIdOrderByGridPosition(from.getId());
        rest.sort(Comparator.comparing(RaceEntry::getCarNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        int car = 1;
        for (RaceEntry entry : rest) {
            entry.setCarNumber(car++);
        }
        List<RaceEntry> gridded = rest.stream().filter(e -> e.getGridPosition() != null)
                .sorted(Comparator.comparing(RaceEntry::getGridPosition)).toList();
        int grid = 1;
        for (RaceEntry entry : gridded) {
            entry.setGridPosition(grid++);
        }
        raceEntryRepository.saveAll(rest);
    }
}
