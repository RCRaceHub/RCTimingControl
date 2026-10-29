package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.Event;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.event.FinalLegsScope;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
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
import dev.monkeypatch.rctiming.query.championship.ChampionshipSeedingQuery;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest.ClassFinalsConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates all Round, Race, and RaceEntry records for an event in a single transaction.
 *
 * <p>Heat assignment (which drivers are in Heat 1 vs Heat 2) is fixed at generation time
 * using a snake-draft algorithm in entry order. The same heat membership persists
 * across all Practice and Qualifying rounds.
 *
 * <p>Finals grids are created empty; seeding is deferred to {@link BumpUpSeedingService}
 * once qualifying completes.
 */
@Service
@Transactional
public class RoundGeneratorService {

    private final RoundRepository roundRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final EntryRepository entryRepository;
    private final EventClassRepository eventClassRepository;
    private final RaceFormatService raceFormatService;
    private final ChampionshipSeedingQuery championshipSeeding;
    private final EventRepository eventRepository;

    public RoundGeneratorService(RoundRepository roundRepository,
                                  RaceRepository raceRepository,
                                  RaceEntryRepository raceEntryRepository,
                                  EntryRepository entryRepository,
                                  EventClassRepository eventClassRepository,
                                  RaceFormatService raceFormatService,
                                  ChampionshipSeedingQuery championshipSeeding,
                                  EventRepository eventRepository) {
        this.roundRepository = roundRepository;
        this.raceRepository = raceRepository;
        this.raceEntryRepository = raceEntryRepository;
        this.entryRepository = entryRepository;
        this.eventClassRepository = eventClassRepository;
        this.raceFormatService = raceFormatService;
        this.championshipSeeding = championshipSeeding;
        this.eventRepository = eventRepository;
    }

    /**
     * Persists all Round, Race, and RaceEntry records in a single transaction.
     *
     * @param request generation parameters
     * @throws StateConflictException if run order already exists for the event, or no class has a confirmed
     *                                entry (HTTP 409); classes with no entries are left out of the run order
     */
    public void generate(RoundGenerationRequest request) {
        if (roundRepository.existsByEventId(request.eventId())) {
            throw new StateConflictException(
                    "Run order already generated for event " + request.eventId());
        }
        GenerationPlan plan = buildPlan(request);
        plan.persist();
    }

    /**
     * Throws away the event's run order and generates it again from the current entries and the request. Refused
     * once any race has left PENDING, since a started race has laps and results the new heats would orphan.
     * The rounds go with their races, grids and result snapshots; the foreign keys cascade.
     * Refused as well when a race has penalties, incidents or marshal records, which don't cascade.
     *
     * @throws StateConflictException if a race of the event has been called, started or finished, or no class has
     *                                a confirmed entry (checked before anything is deleted)
     */
    public void regenerate(RoundGenerationRequest request) {
        List<Round> rounds = roundRepository.findByEventIdOrderBySequenceInEvent(request.eventId());
        for (Round round : rounds) {
            for (Race race : raceRepository.findByRoundIdOrderBySequenceInRound(round.getId())) {
                if (race.getStatus() != RaceStatus.PENDING) {
                    throw new StateConflictException(
                            "The run order can't be regenerated once a race has been called or started");
                }
            }
        }
        if (raceRepository.hasRecordsAgainstEventRaces(request.eventId())) {
            throw new StateConflictException(
                    "The run order can't be regenerated while its races have penalties, incidents, marshal laps "
                            + "or transponder links recorded against them");
        }
        GenerationPlan plan = buildPlan(request);
        rounds.forEach(roundRepository::delete);
        plan.persist();
    }

    /**
     * Sets gridPosition on RaceEntry rows for a specific race using the finishing order
     * from the previous round's same heat. Best finisher (index 0) gets gridPosition=1.
     * Entries missing from the finishing order (cars that never crossed the line, or were added to the heat
     * since) start behind the others, in the order they already had, so the grid is always 1 to n with no gaps
     * or repeats. Car numbers are not touched: they change only at a reseed.
     *
     * <p>Called by the race state machine when a heat finishes, and again when a correction changes that
     * heat's result, to set up start positions for the same heat in the next round. Joins the caller's
     * transaction. Does nothing to a race that has already started.
     *
     * @param newRaceId              the race whose grid positions to update
     * @param entryIdsInFinishingOrder entry IDs ordered best-first from the previous round
     */
    public void applyPreviousRoundFinishingOrder(Long newRaceId,
                                                  List<Long> entryIdsInFinishingOrder) {
        Race race = raceRepository.getOrThrow(newRaceId);
        if (race.getStatus() != RaceStatus.PENDING && race.getStatus() != RaceStatus.GRID) {
            return;
        }
        List<RaceEntry> entries = raceEntryRepository.findByRaceIdOrderByGridPosition(newRaceId);
        Map<Long, Integer> finishPosition = new HashMap<>();
        for (int i = 0; i < entryIdsInFinishingOrder.size(); i++) {
            finishPosition.putIfAbsent(entryIdsInFinishingOrder.get(i), i + 1);
        }
        // Stable sort: finishers by finish, then the rest in their existing order
        List<RaceEntry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingInt(e -> finishPosition.getOrDefault(e.getEntryId(), Integer.MAX_VALUE)));
        int grid = 1;
        for (RaceEntry entry : ordered) {
            entry.setGridPosition(grid++);
            raceEntryRepository.save(entry);
        }
    }

    /**
     * Builds the complete generation plan (rounds, races, heat assignments) without any I/O.
     */
    private GenerationPlan buildPlan(RoundGenerationRequest request) {
        List<EventClass> eventClasses = eventClassRepository.findByEventId(request.eventId());

        // For each class, load CONFIRMED entries and compute snake-draft heat assignment
        // classId -> list of heats (each heat is list of entryIds)
        Map<Long, List<List<Long>>> heatsByClass = new HashMap<>();
        List<EventClass> classesWithEntries = new ArrayList<>();

        for (EventClass ec : eventClasses) {
            List<Entry> entries = entryRepository.findByEventClassIdAndStatus(
                    ec.getId(), EntryStatus.CONFIRMED);
            if (entries.isEmpty()) {
                // No drivers, so no heats and no finals: a class with nobody in it is left out of the run order
                continue;
            }
            classesWithEntries.add(ec);

            // Seed by the class's championship standing, best first; drivers with no points, and every driver
            // when the event is in no championship for the class, keep entry order.
            Map<Long, Integer> standing = championshipSeeding.positionsFor(request.eventId(), ec.getRacingClassId());
            entries.sort(Comparator
                    .comparingInt((Entry e) -> e.getCompetitorId() == null
                            ? Integer.MAX_VALUE
                            : standing.getOrDefault(e.getCompetitorId(), Integer.MAX_VALUE))
                    .thenComparingLong(Entry::getId));

            int heatCount = heatCount(entries.size(), request.maxCarsPerHeat(), request.minCarsPerHeat());

            // Snake draft: fill heats 0..heatCount-1 in snake order
            List<List<Long>> heats = new ArrayList<>();
            for (int h = 0; h < heatCount; h++) {
                heats.add(new ArrayList<>());
            }
            for (int i = 0; i < entries.size(); i++) {
                int pass = i / heatCount;
                int offset = i % heatCount;
                int heatIndex = (pass % 2 == 0) ? offset : (heatCount - 1 - offset);
                heats.get(heatIndex).add(entries.get(i).getId());
            }

            heatsByClass.put(ec.getId(), heats);
        }

        if (classesWithEntries.isEmpty()) {
            throw new StateConflictException(
                    "No drivers are entered in any class of this event, so there is nothing to generate heats for. "
                            + "Add entries or walk-ins first.");
        }
        return new GenerationPlan(request, classesWithEntries, heatsByClass);
    }

    /**
     * Heats for a class: enough to keep each under the maximum, and at least two once the class has
     * {@code minCarsPerHeat} cars for each, since the drivers of one heat marshal the next and a lone
     * heat has no marshals. The snake draft then splits the class evenly.
     */
    static int heatCount(int cars, int maxCarsPerHeat, int minCarsPerHeat) {
        if (cars == 0) {
            return 0;
        }
        int forMax = (int) Math.ceil((double) cars / maxCarsPerHeat);
        return cars >= 2 * minCarsPerHeat ? Math.max(forMax, 2) : forMax;
    }

    /**
     * The worked-out heats for one generation: built without writing anything, so a refused request leaves the
     * database alone, and {@link #persist} then writes rounds in event order (practice, qualifying, then finals).
     */
    private class GenerationPlan {

        private final RoundGenerationRequest request;
        private final List<EventClass> eventClasses;
        private final Map<Long, List<List<Long>>> heatsByClass;

        GenerationPlan(RoundGenerationRequest request,
                       List<EventClass> classesWithEntries,
                       Map<Long, List<List<Long>>> heatsByClass) {
            this.request = request;
            this.eventClasses = classesWithEntries;
            this.heatsByClass = heatsByClass;
        }

        void persist() {
            int sequence = 1;

            // Practice rounds
            for (int r = 1; r <= request.practiceRoundsCount(); r++) {
                sequence = persistRound(RoundType.PRACTICE, r, sequence, r == 1);
            }
            // Qualifying rounds
            for (int r = 1; r <= request.qualifyingRoundsCount(); r++) {
                // The first qualifying round starts in car number order, whatever practice did
                sequence = persistRound(RoundType.QUALIFIER, r, sequence, r == 1);
            }
            // Finals (empty grids — seeded by BumpUpSeedingService after qualifying)
            persistFinals(sequence);
        }

        /**
         * Writes one practice or qualifying round with a race per heat of every class that has entries, and returns the next free
         * sequence number. Only the first round of its kind gets grid positions; later ones are set from the
         * previous round's finish.
         */
        private int persistRound(RoundType type, int roundNumber, int sequenceStart, boolean isFirstRound) {
            Round round = new Round();
            round.setEventId(request.eventId());
            round.setType(type);
            round.setRoundNumber(roundNumber);
            round.setSequenceInEvent(sequenceStart);
            round.setStatus(RoundStatus.PENDING);
            Round savedRound = roundRepository.save(round);

            int seq = sequenceStart;
            for (EventClass ec : eventClasses) {
                List<List<Long>> heats = heatsByClass.getOrDefault(ec.getId(), List.of());
                StartType startType = raceFormatService.startType(ec, type);
                for (int h = 0; h < heats.size(); h++) {
                    Race race = new Race();
                    race.setRoundId(savedRound.getId());
                    race.setEventClassId(ec.getId());
                    race.setHeatNumber(h + 1);
                    race.setSequenceInRound(seq++);
                    race.setFinalLetter(null);
                    race.setStartType(startType);
                    race.setStatus(RaceStatus.PENDING);
                    Race savedRace = raceRepository.save(race);

                    List<Long> entryIds = heats.get(h);
                    // Shikabane heat 1 is a random draw; car numbers stay in entry order
                    List<Long> gridOrder = entryIds;
                    if (type == RoundType.QUALIFIER && isFirstRound
                            && raceFormatService.shikabaneConfig(ec).isPresent()) {
                        gridOrder = new ArrayList<>(entryIds);
                        Collections.shuffle(gridOrder, ThreadLocalRandom.current());
                    }
                    for (int pos = 0; pos < entryIds.size(); pos++) {
                        RaceEntry entry = new RaceEntry();
                        entry.setRaceId(savedRace.getId());
                        entry.setEntryId(entryIds.get(pos));
                        // The first round of its kind: set gridPosition to seed (car number) order within heat
                        // (1-based). Subsequent rounds: gridPosition=null — will be assigned when the
                        // previous round finishes via applyPreviousRoundFinishingOrder.
                        entry.setGridPosition(isFirstRound ? gridOrder.indexOf(entryIds.get(pos)) + 1 : null);
                        entry.setCarNumber(pos + 1);  // car_number = 1-N, same as heat slot order for qualifying heats
                        entry.setBumped(false);
                        raceEntryRepository.save(entry);
                    }
                }
            }
            return seq;
        }

        private void persistFinals(int sequenceStart) {
            // One round per final. Finals run lowest first (C, then B, then A), the bump-ups from each going
            // into the next. The rounds are made in reverse letter order so the lowest final gets the earliest
            // sequenceInEvent; the round number still follows the letter (A is 1).
            FinalLegsScope legsScope = eventRepository.findById(request.eventId())
                    .map(Event::getFinalLegsScope).orElse(FinalLegsScope.A_ONLY);
            for (EventClass ec : eventClasses) {
                int finalsCount = resolveFinalsCount(ec);
                // Remembered so a later finals calculation can put back a final it dropped as unused
                if (!Integer.valueOf(finalsCount).equals(ec.getFinalsCount())) {
                    ec.setFinalsCount(finalsCount);
                    eventClassRepository.save(ec);
                }
                boolean zombie = finalsCount >= 2 && raceFormatService.shikabaneConfig(ec)
                        .map(ShikabaneConfig::zombieRound).orElse(false);
                for (int f = finalsCount - 1; f >= 0; f--) {
                    // The zombie round runs after the lower mains and just before A
                    if (zombie && f == 0) {
                        sequenceStart = persistFinal(ec, BumpUpSeedingService.ZOMBIE_LETTER, finalsCount + 1,
                                sequenceStart, 1);
                    }
                    // The A final, or every final when the event says so, is run in legs
                    int legs = legsScope.appliesTo(f) ? Math.max(1, raceFormatService.finalLegs(ec)) : 1;
                    sequenceStart = persistFinal(ec, String.valueOf((char) ('A' + f)), f + 1, sequenceStart, legs);
                }
            }
        }

        /**
         * One final in a round of its own, with an empty grid; returns the next free sequence in the event. A final
         * of {@code legs} legs has that many races in its round, numbered as heats 1 to {@code legs}.
         */
        private int persistFinal(EventClass ec, String finalLetter, int roundNumber, int sequence, int legs) {
            Round round = new Round();
            round.setEventId(request.eventId());
            round.setType(RoundType.FINAL);
            round.setRoundNumber(roundNumber);
            round.setSequenceInEvent(sequence);
            round.setStatus(RoundStatus.PENDING);
            Round savedRound = roundRepository.save(round);

            for (int leg = 1; leg <= legs; leg++) {
                Race race = new Race();
                race.setRoundId(savedRound.getId());
                race.setEventClassId(ec.getId());
                race.setHeatNumber(leg);
                race.setSequenceInRound(leg);
                race.setFinalLetter(finalLetter);
                race.setStartType(raceFormatService.startType(ec, RoundType.FINAL));
                race.setStatus(RaceStatus.PENDING);
                // Finals start with an empty grid: BumpUpSeedingService seeds it from the
                // qualifying standings and reserves its bump slots. No placeholder rows, since
                // every race_entries row must point at a real entry (#45).
                raceRepository.save(race);
            }
            return sequence + 1;
        }

        /**
         * A class's finals count, from the request's override, then the class, then one final. A Shikabane class
         * has the mains its format says unless the request overrides, and none (the last heat is scored with the others) when it says 0.
         */
        private int resolveFinalsCount(EventClass ec) {
            Optional<ShikabaneConfig> shikabane = raceFormatService.shikabaneConfig(ec);
            if (shikabane.isPresent() && shikabane.get().mains() <= 0) {
                return 0;
            }
            ClassFinalsConfig override = request.classFinalsConfigs().stream()
                    .filter(c -> ec.getId().equals(c.eventClassId()))
                    .findFirst()
                    .orElse(null);
            Integer fromFormat = shikabane.map(c -> Math.min(3, c.mains())).orElse(null);
            return coalesce(override != null ? override.finalsCount() : null,
                    fromFormat != null ? fromFormat : ec.getFinalsCount(), 1);
        }

        private int coalesce(Integer... values) {
            for (Integer v : values) {
                if (v != null) return v;
            }
            return 0;
        }
    }
}
