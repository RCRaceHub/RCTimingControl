package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.StateConflictException;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.event.EventRepository;
import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.query.championship.ChampionshipSeedingQuery;
import dev.monkeypatch.rctiming.service.dto.RoundGenerationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundGeneratorServiceTest {

    // --- Mocks for RoundGeneratorService ---
    @Mock
    private RoundRepository roundRepository;
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceEntryRepository raceEntryRepository;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private EventClassRepository eventClassRepository;
    @Mock
    private RaceFormatService raceFormatService;
    @Mock
    private ChampionshipSeedingQuery championshipSeeding;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private RoundGeneratorService service;

    @Test
    void generate_whenTheEventAlreadyHasARunOrder_isAConflictNotAServerError() {
        when(roundRepository.existsByEventId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.generate(new RoundGenerationRequest(1L, 0, 1, 8, List.of())))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("Run order already generated");
    }

    @Test
    void generate_whenNoClassHasEntries_isAConflictAndWritesNothing() {
        EventClass eventClass = new EventClass();
        eventClass.setId(10L);
        eventClass.setEventId(1L);
        when(roundRepository.existsByEventId(1L)).thenReturn(false);
        when(eventClassRepository.findByEventId(1L)).thenReturn(List.of(eventClass));
        when(entryRepository.findByEventClassIdAndStatus(10L, EntryStatus.CONFIRMED)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.generate(new RoundGenerationRequest(1L, 0, 1, 8, List.of())))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("No drivers are entered");
        verify(roundRepository, never()).save(any(Round.class));
        verify(raceRepository, never()).save(any(Race.class));
    }

    @Test
    void generate_leavesOutAClassWithNoEntriesButKeepsTheOthers() {
        EventClass empty = new EventClass();
        empty.setId(10L);
        empty.setEventId(1L);
        EventClass full = new EventClass();
        full.setId(11L);
        full.setEventId(1L);
        when(eventClassRepository.findByEventId(1L)).thenReturn(List.of(empty, full));
        when(entryRepository.findByEventClassIdAndStatus(10L, EntryStatus.CONFIRMED)).thenReturn(new ArrayList<>());
        Entry entry = new Entry();
        entry.setId(1L);
        entry.setEventClassId(11L);
        when(entryRepository.findByEventClassIdAndStatus(11L, EntryStatus.CONFIRMED))
                .thenReturn(new ArrayList<>(List.of(entry)));
        AtomicLong ids = new AtomicLong();
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round round = inv.getArgument(0);
            round.setId(ids.incrementAndGet());
            return round;
        });
        List<Race> races = new ArrayList<>();
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race race = inv.getArgument(0);
            race.setId(ids.incrementAndGet());
            races.add(race);
            return race;
        });

        service.generate(new RoundGenerationRequest(1L, 0, 1, 8, List.of()));

        // One qualifying heat and one final, both for the class that has a driver
        assertThat(races).hasSize(2).allMatch(r -> r.getEventClassId().equals(11L));
    }

    @Test
    void generate_spreadsTheChampionshipLeadersAcrossTheHeatsAndSeedsNewDriversLast() {
        EventClass eventClass = new EventClass();
        eventClass.setId(10L);
        eventClass.setEventId(1L);
        eventClass.setRacingClassId(5L);
        when(eventClassRepository.findByEventId(1L)).thenReturn(List.of(eventClass));
        List<Entry> entries = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            Entry entry = new Entry();
            entry.setId(id);
            entry.setEventClassId(10L);
            entry.setCompetitorId(10 + id);
            entries.add(entry);
        }
        when(entryRepository.findByEventClassIdAndStatus(10L, EntryStatus.CONFIRMED)).thenReturn(entries);
        // Entry 4's competitor leads the championship and entry 3's is second; entries 1 and 2 have no points
        when(championshipSeeding.positionsFor(1L, 5L)).thenReturn(Map.of(14L, 1, 13L, 2));
        AtomicLong ids = new AtomicLong();
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round round = inv.getArgument(0);
            round.setId(ids.incrementAndGet());
            return round;
        });
        Map<Long, Integer> heatOfRace = new HashMap<>();
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race race = inv.getArgument(0);
            race.setId(ids.incrementAndGet());
            heatOfRace.put(race.getId(), race.getHeatNumber());
            return race;
        });
        Map<Integer, List<Long>> entriesByHeat = new TreeMap<>();
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(inv -> {
            RaceEntry raceEntry = inv.getArgument(0);
            entriesByHeat.computeIfAbsent(heatOfRace.get(raceEntry.getRaceId()), k -> new ArrayList<>())
                    .add(raceEntry.getEntryId());
            return raceEntry;
        });

        service.generate(new RoundGenerationRequest(1L, 0, 1, 2, 1, List.of()));

        // Snake draft over the order 4, 3, 1, 2: the leader and the newcomer in heat 1, second and third in heat 2
        assertThat(entriesByHeat.get(1)).containsExactlyInAnyOrder(4L, 2L);
        assertThat(entriesByHeat.get(2)).containsExactlyInAnyOrder(3L, 1L);
    }

    @Test
    void regenerate_whenNoClassHasEntries_keepsTheExistingRunOrder() {
        Round round = new Round();
        round.setId(5L);
        Race race = new Race();
        race.setStatus(RaceStatus.PENDING);
        EventClass eventClass = new EventClass();
        eventClass.setId(10L);
        when(roundRepository.findByEventIdOrderBySequenceInEvent(1L)).thenReturn(List.of(round));
        when(raceRepository.findByRoundIdOrderBySequenceInRound(5L)).thenReturn(List.of(race));
        when(raceRepository.hasRecordsAgainstEventRaces(1L)).thenReturn(false);
        when(eventClassRepository.findByEventId(1L)).thenReturn(List.of(eventClass));
        when(entryRepository.findByEventClassIdAndStatus(10L, EntryStatus.CONFIRMED)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.regenerate(new RoundGenerationRequest(1L, 0, 1, 8, List.of())))
                .isInstanceOf(StateConflictException.class);
        verify(roundRepository, never()).delete(any(Round.class));
    }

    @Test
    void regenerate_whenARaceHasStarted_isAConflictAndKeepsTheRunOrder() {
        Round round = new Round();
        round.setId(5L);
        Race running = new Race();
        running.setStatus(RaceStatus.RUNNING);
        when(roundRepository.findByEventIdOrderBySequenceInEvent(1L)).thenReturn(List.of(round));
        when(raceRepository.findByRoundIdOrderBySequenceInRound(5L)).thenReturn(List.of(running));

        assertThatThrownBy(() -> service.regenerate(new RoundGenerationRequest(1L, 0, 1, 8, List.of())))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("can't be regenerated");
        verify(roundRepository, never()).delete(any(Round.class));
    }

    @Test
    void regenerate_whenARaceHasPenaltiesOrIncidentsRecorded_isAConflictAndKeepsTheRunOrder() {
        Round round = new Round();
        round.setId(5L);
        Race pending = new Race();
        pending.setStatus(RaceStatus.PENDING);
        when(roundRepository.findByEventIdOrderBySequenceInEvent(1L)).thenReturn(List.of(round));
        when(raceRepository.findByRoundIdOrderBySequenceInRound(5L)).thenReturn(List.of(pending));
        when(raceRepository.hasRecordsAgainstEventRaces(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.regenerate(new RoundGenerationRequest(1L, 0, 1, 8, List.of())))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("penalties");
        verify(roundRepository, never()).delete(any(Round.class));
    }

    @Test
    void heatSplit_fifteenDriversMaxEightPerHeat_createsTwoHeats() {
        // Arrange: one EventClass for event 1
        Long eventId = 1L;
        Long eventClassId = 10L;

        EventClass eventClass = new EventClass();
        eventClass.setId(eventClassId);
        eventClass.setEventId(eventId);
        eventClass.setRacingClassId(null);

        when(eventClassRepository.findByEventId(eventId)).thenReturn(List.of(eventClass));

        // 15 entries with IDs 1..15
        List<Entry> entries = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            Entry entry = new Entry();
            entry.setId(i);
            entry.setEventId(eventId);
            entry.setEventClassId(eventClassId);
            entries.add(entry);
        }
        when(entryRepository.findByEventClassIdAndStatus(eventClassId, EntryStatus.CONFIRMED))
                .thenReturn(entries);

        // Hand out ids as the repositories would, and keep what is saved
        AtomicLong ids = new AtomicLong();
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round round = inv.getArgument(0);
            round.setId(ids.incrementAndGet());
            return round;
        });
        List<Race> races = new ArrayList<>();
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race race = inv.getArgument(0);
            race.setId(ids.incrementAndGet());
            races.add(race);
            return race;
        });
        List<RaceEntry> raceEntries = new ArrayList<>();
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(inv -> {
            RaceEntry raceEntry = inv.getArgument(0);
            raceEntries.add(raceEntry);
            return raceEntry;
        });

        // Act: generate with maxCarsPerHeat=8, 0 practice, 1 qualifying
        RoundGenerationRequest request = new RoundGenerationRequest(
                eventId,
                0,          // practiceRoundsCount
                1,          // qualifyingRoundsCount
                8,          // maxCarsPerHeat
                List.of()   // no class finals overrides
        );
        service.generate(request);

        // Assert: exactly 2 heats for the qualifying round
        // (ceil(15 / 8) = 2)
        List<Race> heats = races.stream()
                .filter(r -> r.getFinalLetter() == null) // exclude finals
                .toList();
        assertThat(heats).extracting(Race::getHeatNumber).containsExactlyInAnyOrder(1, 2);

        // Every driver is in exactly one heat
        assertThat(raceEntries).extracting(RaceEntry::getEntryId)
                .containsExactlyInAnyOrderElementsOf(entries.stream().map(Entry::getId).toList());
    }

    @Test
    void firstQualifyingRound_startsInCarNumberOrderAfterPractice_laterRoundsWaitForTheFinishingOrder() {
        EventClass eventClass = new EventClass();
        eventClass.setId(10L);
        eventClass.setEventId(1L);
        when(eventClassRepository.findByEventId(1L)).thenReturn(List.of(eventClass));
        List<Entry> entries = new ArrayList<>();
        for (long i = 1; i <= 3; i++) {
            Entry entry = new Entry();
            entry.setId(i);
            entry.setEventId(1L);
            entry.setEventClassId(10L);
            entries.add(entry);
        }
        when(entryRepository.findByEventClassIdAndStatus(10L, EntryStatus.CONFIRMED)).thenReturn(entries);
        AtomicLong ids = new AtomicLong();
        Map<Long, Round> rounds = new HashMap<>();
        when(roundRepository.save(any(Round.class))).thenAnswer(inv -> {
            Round round = inv.getArgument(0);
            round.setId(ids.incrementAndGet());
            rounds.put(round.getId(), round);
            return round;
        });
        Map<Long, Race> raceById = new HashMap<>();
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race race = inv.getArgument(0);
            race.setId(ids.incrementAndGet());
            raceById.put(race.getId(), race);
            return race;
        });
        List<RaceEntry> raceEntries = new ArrayList<>();
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(inv -> {
            RaceEntry raceEntry = inv.getArgument(0);
            raceEntries.add(raceEntry);
            return raceEntry;
        });

        service.generate(new RoundGenerationRequest(1L, 2, 2, 8, List.of()));

        // Grid positions by round type and number: the first of each kind is set, the rest wait
        Map<String, List<Integer>> gridByRound = new TreeMap<>();
        for (RaceEntry raceEntry : raceEntries) {
            Round round = rounds.get(raceById.get(raceEntry.getRaceId()).getRoundId());
            gridByRound.computeIfAbsent(round.getType() + " " + round.getRoundNumber(), k -> new ArrayList<>())
                    .add(raceEntry.getGridPosition());
        }
        assertThat(gridByRound.get("PRACTICE 1")).containsExactly(1, 2, 3);
        assertThat(gridByRound.get("PRACTICE 2")).containsOnlyNulls();
        assertThat(gridByRound.get("QUALIFIER 1")).containsExactly(1, 2, 3);
        assertThat(gridByRound.get("QUALIFIER 2")).containsOnlyNulls();
    }

    @Test
    void heatCount_splitsIntoTwoHeatsOnceThereAreEnoughCarsForMarshals() {
        assertThat(RoundGeneratorService.heatCount(0, 10, 2)).isZero();
        assertThat(RoundGeneratorService.heatCount(3, 10, 2)).isEqualTo(1);
        assertThat(RoundGeneratorService.heatCount(4, 10, 2)).isEqualTo(2);
        assertThat(RoundGeneratorService.heatCount(8, 8, 4)).isEqualTo(2);
        assertThat(RoundGeneratorService.heatCount(7, 8, 4)).isEqualTo(1);
        assertThat(RoundGeneratorService.heatCount(11, 10, 2)).isEqualTo(2);
        assertThat(RoundGeneratorService.heatCount(21, 10, 2)).isEqualTo(3);
        assertThat(RoundGeneratorService.heatCount(2, 10, 1)).isEqualTo(2);
        assertThat(new RoundGenerationRequest(1L, 0, 1, 4, 10, List.of()).minCarsPerHeat()).isEqualTo(4);
    }
}
