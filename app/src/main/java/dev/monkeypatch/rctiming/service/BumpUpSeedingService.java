package dev.monkeypatch.rctiming.service;

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
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
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
     * <p>Algorithm (example: 20 drivers, 10/final, bumpCount=2, 2 finals A+B):
     * <pre>
     *   B-Final positions 1–10: drivers ranked 11–20 (lowest qualifiers)
     *   A-Final positions 1–8:  drivers ranked 1–8  (top qualifiers)
     *   A-Final positions 9–10: bump slots, kept as bumpSlots=2 on the A-Final race and
     *                           filled by applyBumpUpResults after the B-Final finishes
     * </pre>
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
        List<Race> zombies = finals.stream().filter(f -> ZOMBIE_LETTER.equals(f.getFinalLetter())).toList();
        finals = new ArrayList<>(finals.stream().filter(f -> !ZOMBIE_LETTER.equals(f.getFinalLetter())).toList());
        for (Race zombie : zombies) {
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

        // Work out regular slot counts per final
        int[] regularSlots = new int[finals.size()];
        for (int fi = 0; fi < finals.size(); fi++) {
            boolean isLowestFinal = (fi == 0);
            regularSlots[fi] = isLowestFinal ? carsPerFinal : carsPerFinal - bumpCount;
        }

        // Two independent pointers: the lowest final draws its regular slots from the
        // worst-ranked end of standings; every other ("non-lowest") final draws from the
        // best-ranked end, processed from the top final downward so the fastest qualifiers
        // always land in the top final first, not in whichever final happens to be processed
        // first. A single bottom-up pointer would drop the very top qualifiers once the bump
        // slots shrink the regular places below the size of the qualifying field.
        List<List<Long>> slotEntriesByFinal = new ArrayList<>(finals.size());
        for (int fi = 0; fi < finals.size(); fi++) {
            slotEntriesByFinal.add(null);
        }

        int bottomPtr = qualifyingStandings.size() - 1;
        int lowestSlots = regularSlots[0];
        List<Long> lowestEntries = new ArrayList<>(lowestSlots);
        for (int s = 0; s < lowestSlots && bottomPtr >= 0; s++) {
            lowestEntries.add(0, qualifyingStandings.get(bottomPtr--)); // prepend to reverse order
        }
        slotEntriesByFinal.set(0, lowestEntries);

        // Bound the top pass at bottomPtr (its value *after* the bottom pass above already
        // ran) so the two passes can never claim the same standings entry twice when the
        // qualifying field is smaller than total regular capacity.
        int topPtr = 0;
        for (int fi = finals.size() - 1; fi >= 1; fi--) {
            int slots = regularSlots[fi];
            List<Long> entries = new ArrayList<>(slots);
            for (int s = 0; s < slots && topPtr <= bottomPtr; s++) {
                entries.add(qualifyingStandings.get(topPtr++));
            }
            slotEntriesByFinal.set(fi, entries);
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
                int carNum = qualifyingStandings.indexOf(entryId) + 1;
                RaceEntry entry = new RaceEntry();
                entry.setRaceId(finalRace.getId());
                entry.setEntryId(entryId);
                entry.setGridPosition(i + 1);
                entry.setCarNumber(carNum > 0 ? carNum : null);  // car_number from qualifying standing position
                entry.setBumped(false);
                raceEntryRepository.save(entry);
            }

            // Reserve bump slots on non-lowest finals; applyBumpUpResults fills them
            boolean topFinal = fi == finals.size() - 1;
            finalRace.setBumpSlots(isLowestFinal ? 0 : bumpCount + (topFinal && !zombies.isEmpty() ? 1 : 0));
            raceRepository.save(finalRace);
        }
    }

    /**
     * Puts everyone who raced a main but is not in the A main on the zombie round's grid, lower mains' drivers in
     * the order of their main (B first). Runs when the main below A finishes, after its drivers have been promoted,
     * so a driver who just moved up is not in it. Replaces any earlier grid.
     */
    private void fillZombieRound(Race zombie, Long eventClassId) {
        List<Race> mains = raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL).stream()
                .filter(f -> f.getFinalLetter() != null && !ZOMBIE_LETTER.equals(f.getFinalLetter()))
                .sorted(Comparator.comparing(Race::getFinalLetter)).toList();
        Set<Long> inA = new java.util.HashSet<>();
        List<Long> zombies = new ArrayList<>();
        for (Race main : mains) {
            for (RaceEntry e : raceEntryRepository.findByRaceIdOrderByGridPosition(main.getId())) {
                if ("A".equals(main.getFinalLetter())) {
                    inA.add(e.getEntryId());
                } else if (!zombies.contains(e.getEntryId())) {
                    zombies.add(e.getEntryId());
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
            entry.setCarNumber(position++);
            entry.setBumped(false);
            raceEntryRepository.save(entry);
        }
    }

    /**
     * Fills bump slots in the next-higher final after a lower final finishes.
     *
     * <p>Finds the next-higher final for the same EventClass (C→B→A) and adds the best finishers
     * not already in it, up to its unfilled bump slots, at the back of its grid with
     * {@code bumped=true}. Grid positions and car numbers carry on from the highest already in
     * that final. A repeat call adds nobody once the slots are full.
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
        if (!zombieRound && nextChar == 'A' && !zombieRaces.isEmpty()) {
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

        List<Long> promoted = new ArrayList<>(openSlots);
        for (Long entryId : finishingOrder) {
            if (promoted.size() >= openSlots) break;
            if (entryId == null || alreadyInFinal.contains(entryId) || promoted.contains(entryId)) continue;
            RaceEntry bumpedEntry = new RaceEntry();
            bumpedEntry.setRaceId(nextFinal.getId());
            bumpedEntry.setEntryId(entryId);
            bumpedEntry.setGridPosition(nextGridPosition++);
            bumpedEntry.setCarNumber(nextCarNumber++);
            bumpedEntry.setBumped(true);
            raceEntryRepository.save(bumpedEntry);
            promoted.add(entryId);
        }
        if (!zombieRound && nextChar == 'A' && !zombieRaces.isEmpty()) {
            fillZombieRound(zombieRaces.get(0), finishedRace.getEventClassId());
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
