package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.audit.Actor;
import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundStatus;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds finals grids from qualifying standings and applies bump-up promotions
 * after a lower final finishes.
 *
 * <p>{@link EventRunOrderService} seeds the finals when the race director asks, after qualifying heats have
 * finished, and the race state machine applies the bump-ups when each lower final finishes.
 */
@Service
@Transactional
public class BumpUpSeedingService {

    private static final Logger log = LoggerFactory.getLogger(BumpUpSeedingService.class);

    /**
     * The final letter of a Shikabane zombie round. It is not a rank in the A, B, C chain: it runs after the
     * lowest mains and before A, and its winner takes the one extra bump place A is given for it.
     */
    public static final String ZOMBIE_LETTER = "Z";

    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final RoundRepository roundRepository;
    private final AuditService audit;

    public BumpUpSeedingService(RaceRepository raceRepository,
                                 RaceEntryRepository raceEntryRepository,
                                 RoundRepository roundRepository,
                                 AuditService audit) {
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.roundRepository = roundRepository;
        this.audit = audit;
    }

    /**
     * Seeds all finals grids for a class after qualifying closes.
     *
     * <p>Algorithm (example: 18 drivers, 10/final, bumpCount=2, 2 finals A+B). The top final takes the best drivers for
     * its regular places, the next final the following block, and the lowest final, which runs first, what is left, so
     * it is the smallest:
     * <pre>
     *   B-Final positions 1–10: drivers ranked 9–18 (the rest)
     *   A-Final positions 1–8:  drivers ranked 1–8  (top qualifiers)
     *   A-Final positions 9–10: bump slots, kept as bumpSlots=2 on the A-Final race and
     *                           filled by applyBumpUpResults after the B-Final finishes
     * </pre>
     *
     * <p>When the field is too small to fill every final, the lowest finals are left empty (a field of 8 with 4 cars a
     * final and three finals gives A and B, and C stays empty), so no empty final sits between two with drivers.
     * The unused finals' races are deleted from the run order (kept, empty, only when something was recorded against
     * one early). The top final is never unused, so a final in legs never loses its later legs. A later calculation
     * with fewer cars per final than the field needs brings a deleted final back first, see
     * {@link #restoreFinals}.
     *
     * <p>A Shikabane zombie round (final letter {@value #ZOMBIE_LETTER}) is left out of the split, its old grid is
     * cleared, and the top final gets one more bump place for the zombie round's winner.
     *
     * <p>Every race_entries row points at a real entry, so a bump slot is a count on the race,
     * not a placeholder row (#45). Re-seeding replaces the final's grid.
     *
     * @param eventClassId        the EventClass to seed
     * @param qualifyingStandings entryIds in standings order (best first = index 0)
     * @param finalsCount         number of finals (1=A, 2=A+B, 3=A+B+C)
     * @param carsPerFinal        total cars per final
     * @param bumpCount           how many promote from each lower final
     */
    public void seedFinals(Long eventClassId,
                           List<Long> qualifyingStandings,
                           int finalsCount,
                           int carsPerFinal,
                           int bumpCount) {
        seedFinals(eventClassId, qualifyingStandings, finalsCount, carsPerFinal, bumpCount, false);
    }

    /**
     * Puts back the A, B, C... finals a class had when its rounds were generated but that an earlier calculation
     * deleted as unused, so a recalculation with fewer cars per final can use them again. Each missing final gets a
     * round of its own, with an empty grid, just before the next final above it in the run order (and before the zombie round, if it has one); the rounds after
     * shift down one place. Nothing happens when no final is missing.
     *
     * @param intendedCount the number of finals the class was generated with ({@code EventClass.finalsCount})
     * @param startType     the start type of the class's finals
     * @return the number of A, B, C finals the class has afterwards
     */
    public int restoreFinals(Long eventClassId, int intendedCount, StartType startType) {
        return restoreFinals(eventClassId, intendedCount, startType, 1);
    }

    /**
     * As {@link #restoreFinals(Long, int, StartType)}, but each restored final is run in {@code legs} legs: races
     * numbered as heats 1 to {@code legs} in its round, for an event that runs every final in legs.
     */
    public int restoreFinals(Long eventClassId, int intendedCount, StartType startType, int legs) {
        List<Race> all = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL);
        Map<String, Race> byLetter = new HashMap<>();
        for (Race race : all) {
            if (!ZOMBIE_LETTER.equals(race.getFinalLetter()) && !FinalLegs.isExtraLeg(race)) {
                byLetter.put(race.getFinalLetter(), race);
            }
        }
        if (byLetter.isEmpty()) {
            return 0;
        }
        Race zombie = all.stream().filter(r -> ZOMBIE_LETTER.equals(r.getFinalLetter())).findFirst().orElse(null);
        // Lowest missing letter first (B before C): each is put just before the final above it, which is there already
        // because the unused finals were dropped from the bottom
        for (int f = 1; f < Math.min(intendedCount, 26); f++) {
            String letter = String.valueOf((char) ('A' + f));
            if (byLetter.containsKey(letter)) {
                continue;
            }
            Race above = byLetter.get(String.valueOf((char) ('A' + f - 1)));
            if (above == null) {
                continue;
            }
            // A zombie round runs after the lowest main and just before A, so B goes in front of it
            Race anchor = f == 1 && zombie != null ? zombie : above;
            Round aboveRound = roundRepository.findById(anchor.getRoundId()).orElseThrow();
            int sequence = aboveRound.getSequenceInEvent();
            // A final added to the run order puts any reordering of the finals back to the generated order
            raceRepository.clearRunRanks(aboveRound.getEventId());
            for (Round later : roundRepository.findByEventIdOrderBySequenceInEvent(aboveRound.getEventId())) {
                if (later.getSequenceInEvent() >= sequence) {
                    later.setSequenceInEvent(later.getSequenceInEvent() + 1);
                    roundRepository.save(later);
                }
            }
            Round round = new Round();
            round.setEventId(aboveRound.getEventId());
            round.setType(RoundType.FINAL);
            round.setRoundNumber(f + 1);
            round.setSequenceInEvent(sequence);
            round.setStatus(RoundStatus.PENDING);
            round = roundRepository.save(round);
            for (int leg = 1; leg <= Math.max(1, legs); leg++) {
                Race race = new Race();
                race.setRoundId(round.getId());
                race.setEventClassId(eventClassId);
                race.setHeatNumber(leg);
                race.setSequenceInRound(leg);
                race.setFinalLetter(letter);
                race.setStartType(startType);
                race.setStatus(RaceStatus.PENDING);
                Race saved = raceRepository.save(race);
                if (leg == 1) {
                    byLetter.put(letter, saved);
                }
            }
        }
        return byLetter.size();
    }

    /**
     * As {@link #seedFinals(Long, List, int, int, int)}, but with {@code fillTopFirst} every final above the lowest
     * takes a full {@code carsPerFinal} (the layout has no bump spots, so {@code bumpCount} is ignored), and unused
     * lowest finals are kept rather than dropped.
     */
    public void seedFinals(Long eventClassId,
                           List<Long> qualifyingStandings,
                           int finalsCount,
                           int carsPerFinal,
                           int bumpCount,
                           boolean fillTopFirst) {
        // Load all final races for this event class
        List<Race> finals = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL);
        if (finals.isEmpty()) {
            throw new StateConflictException("This class has no finals to seed; generate the rounds first");
        }
        // Seeding replaces every grid, which would wipe a final that is under way or done, and the bumps already made
        if (finals.stream().anyMatch(f -> f.getStatus() != RaceStatus.PENDING)) {
            throw new StateConflictException("A final for this class has already started, so it can't be seeded again");
        }
        // A zombie round has no qualifiers of its own: it is filled once the lower mains are done, and it gives the
        // A main one more bump place for its winner
        List<Race> zombieRaces = finals.stream().filter(f -> ZOMBIE_LETTER.equals(f.getFinalLetter())).toList();
        // Later legs of a final copy leg 1's grid at the end, so they are not finals of their own here
        finals = new ArrayList<>(finals.stream().filter(f -> !ZOMBIE_LETTER.equals(f.getFinalLetter()))
                .filter(f -> !FinalLegs.isExtraLeg(f)).toList());
        if (finals.isEmpty()) {
            throw new StateConflictException("This class has no mains to seed; generate the rounds first");
        }
        for (Race zombie : zombieRaces) {
            raceEntryRepository.deleteAll(raceEntryRepository.findByRaceIdOrderByGridPosition(zombie.getId()));
            zombie.setBumpSlots(0);
            raceRepository.save(zombie);
        }
        // Sort by finalLetter DESC: C before B before A (lowest final first for assignment)
        finals.sort(Comparator.comparing(Race::getFinalLetter).reversed());

        // Build assignment list for each final (from lowest to highest letter)
        // Lowest final: positions 1..carsPerFinal = ranks (qualifyingStandings.size()-carsPerFinal+1)..last
        // Higher finals: positions 1..(carsPerFinal-bumpCount) = next block of regular qualifiers
        //                positions (carsPerFinal-bumpCount+1)..carsPerFinal = bump slots (Race.bumpSlots)

        if (fillTopFirst) {
            bumpCount = 0;
        }
        // A field too small for every final leaves the lowest finals empty, not one in the middle: B between a full A
        // and C would be skipped over. Only as many finals as the field needs are used, counting from the top.
        if (!fillTopFirst) {
            int needed = finalsNeeded(qualifyingStandings.size(), carsPerFinal, bumpCount);
            while (finals.size() > needed) {
                Race unused = finals.remove(0);
                raceEntryRepository.deleteAll(raceEntryRepository.findByRaceIdOrderByGridPosition(unused.getId()));
                if (raceRepository.hasRecordsAgainstRace(unused.getId())) {
                    // A penalty or incident was raised against it early: keep the race rather than lose the record
                    unused.setBumpSlots(0);
                    raceRepository.save(unused);
                } else {
                    Long roundId = unused.getRoundId();
                    roundRepository.findById(roundId).ifPresent(r -> raceRepository.clearRunRanks(r.getEventId()));
                    raceRepository.delete(unused);
                    if (raceRepository.findByRoundIdOrderBySequenceInRound(roundId).isEmpty()) {
                        roundRepository.deleteById(roundId);
                    }
                }
            }
        }
        // Work out regular slot counts per final
        int[] regularSlots = new int[finals.size()];
        for (int fi = 0; fi < finals.size(); fi++) {
            boolean isLowestFinal = (fi == 0);
            regularSlots[fi] = isLowestFinal ? carsPerFinal : carsPerFinal - bumpCount;
        }

        // Filled from the top final down, so the fastest qualifiers always land in the top final first
        List<List<Long>> slotEntriesByFinal = new ArrayList<>(finals.size());
        for (int fi = 0; fi < finals.size(); fi++) {
            slotEntriesByFinal.add(null);
        }

        // Top final first: each takes its regular places and the lowest final, which runs first, takes what is left, so
        // it is the smallest. The caller has sized the grid so the rest fits the lowest final.
        int next = 0;
        for (int fi = finals.size() - 1; fi >= 0; fi--) {
            int remaining = qualifyingStandings.size() - next;
            int take = fi == 0 ? remaining : Math.min(regularSlots[fi], remaining);
            slotEntriesByFinal.set(fi, new ArrayList<>(qualifyingStandings.subList(next, next + Math.max(0, take))));
            next += Math.max(0, take);
        }

        for (int fi = 0; fi < finals.size(); fi++) {
            Race finalRace = finals.get(fi);
            boolean isLowestFinal = (fi == 0);
            List<Long> slotEntries = slotEntriesByFinal.get(fi);

            // Replace any earlier seeding of this final
            List<RaceEntry> existing = raceEntryRepository.findByRaceIdOrderByGridPosition(finalRace.getId());
            raceEntryRepository.deleteAll(existing);

            // Create regular slot entries
            for (int i = 0; i < slotEntries.size(); i++) {
                long entryId = slotEntries.get(i);
                RaceEntry entry = new RaceEntry();
                entry.setRaceId(finalRace.getId());
                entry.setEntryId(entryId);
                entry.setGridPosition(i + 1);
                entry.setCarNumber(i + 1);  // each final numbers its cars 1..n by grid place, as each heat does
                entry.setBumped(false);
                raceEntryRepository.save(entry);
            }

            // Reserve bump slots on non-lowest finals; applyBumpUpResults fills them
            boolean topFinal = fi == finals.size() - 1;
            finalRace.setBumpSlots(isLowestFinal ? 0 : bumpCount + (topFinal && !zombieRaces.isEmpty() ? 1 : 0));
            raceRepository.save(finalRace);
        }
        FinalLegs.syncGrids(raceRepository, raceEntryRepository, eventClassId);
    }

    /**
     * The fewest finals that hold a field of {@code field} drivers: the lowest takes {@code carsPerFinal}, each one
     * above it {@code carsPerFinal - bumpCount} regular places (the rest are bump places). At least 1.
     */
    static int finalsNeeded(int field, int carsPerFinal, int bumpCount) {
        int perUpperFinal = Math.max(1, carsPerFinal - bumpCount);
        return field <= carsPerFinal ? 1 : 1 + (field - carsPerFinal + perUpperFinal - 1) / perUpperFinal;
    }

    /**
     * Puts everyone who raced a main but is not in the A main on the zombie round's grid, lower mains' drivers in
     * the order of their main (B first). Runs when the main below A finishes, after its drivers have been promoted,
     * so a driver who just moved up is not in it. Replaces any earlier grid, and keeps each driver's car number from
     * their main. Called from {@link #applyBumpUpResults} inside the finish's transaction, only while the zombie round
     * is still pending.
     *
     * @param zombie       the zombie round's race
     * @param eventClassId the class whose mains are read
     */
    private void fillZombieRound(Race zombie, Long eventClassId) {
        List<Race> mains = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL).stream()
                .filter(f -> f.getFinalLetter() != null && !ZOMBIE_LETTER.equals(f.getFinalLetter()))
                .sorted(Comparator.comparing(Race::getFinalLetter)).toList();
        Set<Long> inA = new HashSet<>();
        Set<Long> zombies = new LinkedHashSet<>();
        Map<Long, Integer> carNumbers = new HashMap<>();
        for (Race main : mains) {
            for (RaceEntry e : raceEntryRepository.findByRaceIdOrderByGridPosition(main.getId())) {
                if ("A".equals(main.getFinalLetter())) {
                    inA.add(e.getEntryId());
                } else {
                    zombies.add(e.getEntryId());
                    if (e.getCarNumber() != null) {
                        carNumbers.putIfAbsent(e.getEntryId(), e.getCarNumber());
                    }
                }
            }
        }
        zombies.removeAll(inA);
        raceEntryRepository.deleteAll(raceEntryRepository.findByRaceIdOrderByGridPosition(zombie.getId()));
        int position = 1;
        for (Long entryId : zombies) {
            RaceEntry entry = new RaceEntry();
            entry.setRaceId(zombie.getId());
            entry.setEntryId(entryId);
            entry.setGridPosition(position);
            entry.setCarNumber(carNumbers.getOrDefault(entryId, position));
            position++;
            entry.setBumped(false);
            raceEntryRepository.save(entry);
        }
    }

    /**
     * Fills bump slots in the next-higher final after a lower final finishes.
     *
     * <p>Finds the next-higher final for the same EventClass (C→B→A, and the zombie round's winner into A) and adds the best finishers
     * not already in it, up to its unfilled bump slots, at the back of its grid with
     * {@code bumped=true}. Grid positions carry on from the highest already in that final, in finishing order (the
     * winner of the lower final takes the first bump place), and each driver's car number is their grid position
     * (the next free number if it is taken). A repeat call adds nobody once the slots are full.
     *
     * @param finishedFinalRaceId the Race ID of the final that just finished
     * @param finishingOrder      entry IDs of the finished final's drivers, best first
     * @return the entry IDs promoted by this call, best first; none when there is no higher final
     */
    public List<Long> applyBumpUpResults(Long finishedFinalRaceId, List<Long> finishingOrder) {
        Race finishedRace = raceRepository.findById(finishedFinalRaceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Race not found: " + finishedFinalRaceId));

        String currentLetter = finishedRace.getFinalLetter();
        if (currentLetter == null || currentLetter.isEmpty()) {
            throw new IllegalArgumentException(
                    "Race " + finishedFinalRaceId + " is not a final");
        }

        // Next higher final: 'C' → 'B', 'B' → 'A', and the zombie round's winner goes to 'A'. With none there is nobody to move up; this runs inside the
        // finish, so it must not throw for that (the finish would be rolled back, #217)
        boolean zombieRound = ZOMBIE_LETTER.equals(currentLetter);
        char nextChar = zombieRound ? 'A' : (char) (currentLetter.charAt(0) - 1);
        if (nextChar < 'A') {
            return List.of();
        }
        String nextFinalLetter = String.valueOf(nextChar);

        // Find the next final race for the same EventClass
        List<Race> nextFinals = raceRepository.findByEventClassIdAndFinalLetter(
                finishedRace.getEventClassId(), nextFinalLetter);
        if (nextFinals.isEmpty()) {
            log.warn("Bump-up: no {} final for event class {}, so nobody moves up from race {}",
                    nextFinalLetter, finishedRace.getEventClassId(), finishedFinalRaceId);
            return List.of();
        }
        Race nextFinal = nextFinals.get(0);

        List<RaceEntry> grid = raceEntryRepository.findByRaceIdOrderByGridPosition(nextFinal.getId());
        long alreadyBumped = grid.stream().filter(RaceEntry::isBumped).count();
        int openSlots = (int) Math.max(0, nextFinal.getBumpSlots() - alreadyBumped);
        // The place kept for the zombie round's winner is not for the main below it
        List<Race> zombieRaces = raceRepository.findByEventClassIdAndFinalLetter(
                finishedRace.getEventClassId(), ZOMBIE_LETTER);
        if (!zombieRound && nextChar == 'A' && !zombieRaces.isEmpty()
                && zombieRaces.get(0).getStatus() != RaceStatus.FINISHED) {
            openSlots = Math.max(0, openSlots - 1);
        }
        if (zombieRound) {
            openSlots = Math.min(openSlots, 1);
        }
        Set<Long> alreadyInFinal = grid.stream().map(RaceEntry::getEntryId).collect(Collectors.toSet());
        int nextGridPosition = grid.stream().map(RaceEntry::getGridPosition).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
        int nextCarNumber = grid.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
        // A bumped driver takes the car number of their place on the grid they move to, so the bump-ups read 9, 10 in
        // finishing order and not whatever qualifying rank they had in the lower final (which ran the other way when
        // the winner qualified lowest). The next free number stands in when that one is taken.
        Set<Integer> numbersInUse = grid.stream().map(RaceEntry::getCarNumber).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> promoted = new ArrayList<>(openSlots);
        for (Long entryId : finishingOrder) {
            if (promoted.size() >= openSlots) break;
            if (entryId == null || alreadyInFinal.contains(entryId) || promoted.contains(entryId)) continue;
            RaceEntry bumpedEntry = new RaceEntry();
            bumpedEntry.setRaceId(nextFinal.getId());
            bumpedEntry.setEntryId(entryId);
            int gridPosition = nextGridPosition++;
            bumpedEntry.setGridPosition(gridPosition);
            if (numbersInUse.add(gridPosition)) {
                bumpedEntry.setCarNumber(gridPosition);
            } else {
                while (!numbersInUse.add(nextCarNumber)) {
                    nextCarNumber++;
                }
                bumpedEntry.setCarNumber(nextCarNumber++);
            }
            bumpedEntry.setBumped(true);
            raceEntryRepository.save(bumpedEntry);
            promoted.add(entryId);
        }
        if (!zombieRound && nextChar == 'A' && !zombieRaces.isEmpty()) {
            // Not once the zombie round is under way or done: re-finishing the main below must not wipe its grid
            if (zombieRaces.get(0).getStatus() == RaceStatus.PENDING) {
                fillZombieRound(zombieRaces.get(0), finishedRace.getEventClassId());
            }
        }
        if (!promoted.isEmpty()) {
            // Nobody asked for this: it follows a final finishing, so the system is the actor
            Long eventId = roundRepository.findById(nextFinal.getRoundId()).map(Round::getEventId).orElse(null);
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("fromRaceId", finishedFinalRaceId);
            after.put("promotedEntryIds", promoted);
            audit.entry(Actor.system("bump-up"), "BUMP_UP_APPLIED").entity("race", nextFinal.getId())
                    .race(nextFinal.getId()).event(eventId)
                    .summary("Moved " + promoted.size() + " up from the " + currentLetter + " final (race "
                            + finishedFinalRaceId + ") into the " + nextFinalLetter + " final (race "
                            + nextFinal.getId() + ")")
                    .after(after).record();
        }
        return promoted;
    }
}
