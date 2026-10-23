package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BumpUpSeedingService#seedFinals} previously had no dedicated test coverage — this
 * class exercises it directly against the algorithm's own documented worked example (20
 * qualifiers, 2 finals A+B, 10 cars/final, bumpCount=2), which the original implementation did
 * not actually satisfy: it silently dropped the top 2 qualifiers from any regular slot instead
 * of seeding them into the A-Final. The fix makes non-lowest finals draw from the top of
 * standings (processed best-final-first) while the lowest final still draws from the bottom.
 */
@ExtendWith(MockitoExtension.class)
class BumpUpSeedingServiceTest {

    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceEntryRepository raceEntryRepository;

    private BumpUpSeedingService service() {
        return new BumpUpSeedingService(raceRepository, raceEntryRepository,
                org.mockito.Mockito.mock(dev.monkeypatch.rctiming.domain.race.RoundRepository.class),
                org.mockito.Mockito.mock(dev.monkeypatch.rctiming.domain.audit.AuditService.class,
                        org.mockito.Mockito.RETURNS_DEEP_STUBS));
    }

    @Test
    void seedFinals_twoFinals_topQualifiersSeedIntoAFinal_bottomQualifiersSeedIntoBFinal() {
        Long eventClassId = 10L;
        Race bFinal = new Race();
        bFinal.setId(200L);
        bFinal.setEventClassId(eventClassId);
        bFinal.setFinalLetter("B");
        Race aFinal = new Race();
        aFinal.setId(201L);
        aFinal.setEventClassId(eventClassId);
        aFinal.setFinalLetter("A");

        when(raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL))
                .thenReturn(new ArrayList<>(List.of(bFinal, aFinal)));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(bFinal.getId())).thenReturn(List.of());
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(List.of());

        // Ranks 1..20 (index 0 = rank 1, best).
        List<Long> standings = new ArrayList<>();
        for (long i = 1; i <= 20; i++) standings.add(i);

        service().seedFinals(eventClassId, standings, 2, 10, 2);

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(18)).save(captor.capture());
        List<RaceEntry> saved = captor.getAllValues();

        List<RaceEntry> bEntries = saved.stream().filter(e -> e.getRaceId().equals(bFinal.getId()))
                .sorted(Comparator.comparingInt(RaceEntry::getGridPosition)).toList();
        List<RaceEntry> aEntries = saved.stream().filter(e -> e.getRaceId().equals(aFinal.getId()))
                .sorted(Comparator.comparingInt(RaceEntry::getGridPosition)).toList();

        // B-Final: 10 regular slots, no bump reservation (lowest final) — ranks 11..20.
        assertThat(bEntries).hasSize(10);
        assertThat(bEntries).extracting(RaceEntry::getEntryId)
                .containsExactly(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
        assertThat(bEntries).allMatch(e -> !e.isBumped());

        // A-Final: 8 regular slots (the TOP qualifiers, ranks 1..8) and 2 bump slots kept as a
        // count on the race, not as placeholder rows (#45).
        assertThat(aEntries).extracting(RaceEntry::getEntryId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(aEntries).allMatch(e -> !e.isBumped());
        assertThat(aFinal.getBumpSlots()).isEqualTo(2);
        assertThat(bFinal.getBumpSlots()).isZero();
        verify(raceRepository).save(aFinal);
        verify(raceRepository).save(bFinal);
    }

    @Test
    void seedFinals_singleFinal_allRegularSlotsFromTopOfStandings() {
        Long eventClassId = 20L;
        Race aFinal = new Race();
        aFinal.setId(300L);
        aFinal.setEventClassId(eventClassId);
        aFinal.setFinalLetter("A");

        when(raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL))
                .thenReturn(new ArrayList<>(List.of(aFinal)));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(List.of());

        List<Long> standings = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        service().seedFinals(eventClassId, standings, 1, 10, 0);

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(10)).save(captor.capture());
        List<RaceEntry> saved = captor.getAllValues().stream()
                .sorted(Comparator.comparingInt(RaceEntry::getGridPosition)).toList();

        assertThat(saved).extracting(RaceEntry::getEntryId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(saved).allMatch(e -> !e.isBumped());
    }

    @Test
    void seedFinals_fewerQualifiersThanTotalCapacity_doesNotThrow_leavesShortfallUnassigned() {
        Long eventClassId = 30L;
        Race bFinal = new Race();
        bFinal.setId(400L);
        bFinal.setEventClassId(eventClassId);
        bFinal.setFinalLetter("B");
        Race aFinal = new Race();
        aFinal.setId(401L);
        aFinal.setEventClassId(eventClassId);
        aFinal.setFinalLetter("A");

        when(raceRepository.findByEventClassIdAndRoundType(eventClassId, RoundType.FINAL))
                .thenReturn(new ArrayList<>(List.of(bFinal, aFinal)));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(bFinal.getId())).thenReturn(List.of());
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(List.of());

        // Only 5 qualifiers total, far fewer than the 18 regular + 2 bump capacity.
        List<Long> standings = List.of(1L, 2L, 3L, 4L, 5L);

        service().seedFinals(eventClassId, standings, 2, 10, 2);

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        // 5 B-final regular slots + 0 A-final regular slots = 5 saves.
        verify(raceEntryRepository, times(5)).save(captor.capture());
        List<RaceEntry> saved = captor.getAllValues();

        List<RaceEntry> bEntries = saved.stream().filter(e -> e.getRaceId().equals(bFinal.getId())).toList();
        List<RaceEntry> aRegular = saved.stream()
                .filter(e -> e.getRaceId().equals(aFinal.getId()) && !e.isBumped()).toList();

        // Lowest final (B) draws from the bottom: with only 5 standings and 10 lowest-final
        // slots, all 5 land in B.
        assertThat(bEntries).hasSize(5);
        assertThat(bEntries).extracting(RaceEntry::getEntryId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        // Top-down pass for A starts after the bottom pass has already consumed everything
        // (both pointers walk the same list), so no regular slots remain for A here.
        assertThat(aRegular).isEmpty();
        // The A-final still keeps its bump slots even though no regular qualifiers were seated there.
        assertThat(aFinal.getBumpSlots()).isEqualTo(2);
    }

    @Test
    void applyBumpUpResults_addsTopFinishersToTheBackOfTheNextFinal() {
        Race bFinal = finalRace(200L, "B");
        Race aFinal = finalRace(201L, "A");
        aFinal.setBumpSlots(2);
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(seededGrid(aFinal, 8));

        List<Long> promoted = service().applyBumpUpResults(bFinal.getId(), List.of(501L, 502L, 503L));

        assertThat(promoted).containsExactly(501L, 502L);
        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RaceEntry::getEntryId).containsExactly(501L, 502L);
        assertThat(captor.getAllValues()).extracting(RaceEntry::getGridPosition).containsExactly(9, 10);
        assertThat(captor.getAllValues()).extracting(RaceEntry::getCarNumber).containsExactly(9, 10);
        assertThat(captor.getAllValues()).allMatch(RaceEntry::isBumped);
        assertThat(captor.getAllValues()).allMatch(e -> e.getRaceId().equals(aFinal.getId()));
    }

    @Test
    void applyBumpUpResults_numbersTheBumpedCarsByTheirPlaceOnTheGridWhateverTheyQualified() {
        Race bFinal = finalRace(200L, "B");
        Race aFinal = finalRace(201L, "A");
        aFinal.setBumpSlots(2);
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(seededGrid(aFinal, 8));
        RaceEntry b1 = new RaceEntry();
        b1.setEntryId(501L);
        b1.setCarNumber(15);
        RaceEntry b2 = new RaceEntry();
        b2.setEntryId(502L);
        b2.setCarNumber(12);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(bFinal.getId())).thenReturn(List.of(b1, b2));

        service().applyBumpUpResults(bFinal.getId(), List.of(501L, 502L, 503L));

        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(RaceEntry::getEntryId).containsExactly(501L, 502L);
        assertThat(captor.getAllValues()).extracting(RaceEntry::getGridPosition).containsExactly(9, 10);
        assertThat(captor.getAllValues()).extracting(RaceEntry::getCarNumber).containsExactly(9, 10);
    }

    @Test
    void zombieRound_takesOnePlaceFromTheMainBelowAIntoTheZombieGrid() {
        Race bFinal = finalRace(200L, "B");
        Race aFinal = finalRace(201L, "A");
        Race zombie = finalRace(202L, "Z");
        aFinal.setBumpSlots(2);
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "Z")).thenReturn(List.of(zombie));
        when(raceRepository.findByEventClassIdAndRoundType(10L, RoundType.FINAL))
                .thenReturn(List.of(aFinal, bFinal, zombie));
        List<RaceEntry> aGrid = seededGrid(aFinal, 2);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(aGrid);
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(zombie.getId())).thenReturn(List.of());
        // A promoted driver is on A's grid when the zombie round is filled
        when(raceEntryRepository.save(any(RaceEntry.class))).thenAnswer(inv -> {
            RaceEntry saved = inv.getArgument(0);
            if (saved.getRaceId().equals(aFinal.getId())) {
                aGrid.add(saved);
            }
            return saved;
        });
        List<RaceEntry> bGrid = new ArrayList<>();
        bGrid.add(raceEntry(bFinal, 501L, 1, false));
        bGrid.add(raceEntry(bFinal, 502L, 2, false));
        bGrid.add(raceEntry(bFinal, 503L, 3, false));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(bFinal.getId())).thenReturn(bGrid);

        // A has two bump places, one of them kept for the zombie round's winner
        List<Long> promoted = service().applyBumpUpResults(bFinal.getId(), List.of(501L, 502L, 503L));

        assertThat(promoted).containsExactly(501L);
        ArgumentCaptor<RaceEntry> captor = ArgumentCaptor.forClass(RaceEntry.class);
        verify(raceEntryRepository, times(3)).save(captor.capture());
        List<RaceEntry> onZombie = captor.getAllValues().stream()
                .filter(e -> e.getRaceId().equals(zombie.getId())).toList();
        assertThat(onZombie).extracting(RaceEntry::getEntryId).containsExactly(502L, 503L);
    }

    @Test
    void zombieRound_winnerTakesTheKeptPlaceInA() {
        Race aFinal = finalRace(201L, "A");
        Race zombie = finalRace(202L, "Z");
        aFinal.setBumpSlots(2);
        List<RaceEntry> aGrid = seededGrid(aFinal, 2);
        aGrid.add(raceEntry(aFinal, 501L, 3, true));
        when(raceRepository.findById(zombie.getId())).thenReturn(Optional.of(zombie));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "Z")).thenReturn(List.of(zombie));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(aGrid);

        List<Long> promoted = service().applyBumpUpResults(zombie.getId(), List.of(502L, 503L));

        assertThat(promoted).containsExactly(502L);
    }

    @Test
    void applyBumpUpResults_repeatCallPromotesNobodyOnceSlotsAreFull() {
        Race bFinal = finalRace(200L, "B");
        Race aFinal = finalRace(201L, "A");
        aFinal.setBumpSlots(2);
        List<RaceEntry> grid = seededGrid(aFinal, 8);
        grid.add(raceEntry(aFinal, 501L, 9, true));
        grid.add(raceEntry(aFinal, 502L, 10, true));
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(grid);

        assertThat(service().applyBumpUpResults(bFinal.getId(), List.of(501L, 502L, 503L))).isEmpty();
        verify(raceEntryRepository, never()).save(any());
    }

    @Test
    void applyBumpUpResults_skipsDriversAlreadyInTheNextFinal() {
        Race bFinal = finalRace(200L, "B");
        Race aFinal = finalRace(201L, "A");
        aFinal.setBumpSlots(2);
        List<RaceEntry> grid = seededGrid(aFinal, 8);
        grid.add(raceEntry(aFinal, 501L, 9, true));
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of(aFinal));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(aFinal.getId())).thenReturn(grid);

        assertThat(service().applyBumpUpResults(bFinal.getId(), List.of(501L, 502L, 503L)))
                .containsExactly(502L);
    }

    @Test
    void applyBumpUpResults_withNoHigherFinal_promotesNobodyAndDoesNotThrow() {
        Race aFinal = finalRace(201L, "A");
        Race bFinal = finalRace(200L, "B");
        when(raceRepository.findById(aFinal.getId())).thenReturn(Optional.of(aFinal));
        when(raceRepository.findById(bFinal.getId())).thenReturn(Optional.of(bFinal));
        when(raceRepository.findByEventClassIdAndFinalLetter(10L, "A")).thenReturn(List.of());

        assertThat(service().applyBumpUpResults(aFinal.getId(), List.of(501L))).isEmpty();
        assertThat(service().applyBumpUpResults(bFinal.getId(), List.of(501L))).as("no A final in the class").isEmpty();
        verify(raceEntryRepository, never()).save(any());
    }

    private static Race finalRace(long id, String letter) {
        Race race = new Race();
        race.setId(id);
        race.setEventClassId(10L);
        race.setFinalLetter(letter);
        return race;
    }

    /** A final's grid as seeded: entries 101.. at positions and car numbers 1..count. */
    private static List<RaceEntry> seededGrid(Race race, int count) {
        List<RaceEntry> grid = new ArrayList<>();
        for (int pos = 1; pos <= count; pos++) {
            grid.add(raceEntry(race, 100L + pos, pos, false));
        }
        return grid;
    }

    private static RaceEntry raceEntry(Race race, long entryId, int position, boolean bumped) {
        RaceEntry e = new RaceEntry();
        e.setRaceId(race.getId());
        e.setEntryId(entryId);
        e.setGridPosition(position);
        e.setCarNumber(position);
        e.setBumped(bumped);
        return e;
    }
}
