package dev.monkeypatch.rctiming.timing;

import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.entry.Entry;
import dev.monkeypatch.rctiming.domain.entry.EntryRepository;
import dev.monkeypatch.rctiming.domain.entry.EntryStatus;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceAuditLabels;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.timing.dto.WrongHeatDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who a number belongs to when it is not in the running race (#856). */
class WrongHeatFinderTest {

    private final RaceAuditLabels labels = mock(RaceAuditLabels.class);
    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private final RaceEntryRepository raceEntryRepository = mock(RaceEntryRepository.class);
    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final CompetitorRepository competitorRepository = mock(CompetitorRepository.class);
    private WrongHeatFinder finder;

    @BeforeEach
    void setUp() {
        finder = new WrongHeatFinder(labels, entryRepository, raceEntryRepository, raceRepository,
                competitorRepository);
        when(labels.eventOf(7L)).thenReturn(5L);
        Race thisRace = race(7L, 1);
        Race home = race(8L, 2);
        when(raceRepository.findById(7L)).thenReturn(Optional.of(thisRace));
        when(raceRepository.findByRoundIdOrderBySequenceInRound(3L)).thenReturn(List.of(thisRace, home));
        when(raceEntryRepository.findByRaceIdOrderByGridPosition(7L)).thenReturn(List.of(slot(7L, 1L, 1)));
        when(raceEntryRepository.findByRaceIdAndEntryId(8L, 2L)).thenReturn(Optional.of(slot(8L, 2L, 4)));
        when(raceEntryRepository.findByRaceIdAndEntryId(7L, 2L)).thenReturn(Optional.empty());
        Competitor sam = new Competitor();
        sam.setId(20L);
        sam.setDisplayName("Sam Ito");
        when(competitorRepository.findById(20L)).thenReturn(Optional.of(sam));
    }

    @Test
    void aNumberHeldByOneDriverInAnotherHeat_namesThemAndTheirHeat() {
        when(entryRepository.findByEventId(5L)).thenReturn(List.of(entry(2L, 20L, "2002", EntryStatus.CONFIRMED)));

        WrongHeatDto flag = finder.find(7L, "2002").orElseThrow();

        assertThat(flag.entryId()).isEqualTo(2L);
        assertThat(flag.driverName()).isEqualTo("Sam Ito");
        assertThat(flag.carNumber()).isEqualTo(4);
        assertThat(flag.heat()).isEqualTo("Heat 2");
        assertThat(flag.status()).isEqualTo(WrongHeatDto.Status.OPEN);
    }

    @Test
    void aNumberNobodyHolds_isNotAWrongHeat() {
        when(entryRepository.findByEventId(5L)).thenReturn(List.of(entry(2L, 20L, "2002", EntryStatus.CONFIRMED)));

        assertThat(finder.find(7L, "9999")).isEmpty();
    }

    @Test
    void aWithdrawnEntry_isIgnored() {
        when(entryRepository.findByEventId(5L)).thenReturn(List.of(entry(2L, 20L, "2002", EntryStatus.WITHDRAWN)));

        assertThat(finder.find(7L, "2002")).isEmpty();
    }

    @Test
    void aNumberHeldByDifferentCompetitors_isLeftToTheReferee() {
        when(entryRepository.findByEventId(5L)).thenReturn(List.of(
                entry(2L, 20L, "2002", EntryStatus.CONFIRMED), entry(3L, 21L, "2002", EntryStatus.CONFIRMED)));

        assertThat(finder.find(7L, "2002")).isEmpty();
    }

    @Test
    void aNumberAnEntryInTheRaceHolds_isNotAWrongHeat() {
        when(entryRepository.findByEventId(5L)).thenReturn(List.of(
                entry(1L, 20L, "2002", EntryStatus.CONFIRMED), entry(2L, 20L, "2002", EntryStatus.CONFIRMED)));

        assertThat(finder.find(7L, "2002")).isEmpty();
    }

    private static Race race(long id, int heat) {
        Race race = new Race();
        race.setId(id);
        race.setRoundId(3L);
        race.setHeatNumber(heat);
        return race;
    }

    private static RaceEntry slot(long raceId, long entryId, int car) {
        RaceEntry raceEntry = new RaceEntry();
        raceEntry.setRaceId(raceId);
        raceEntry.setEntryId(entryId);
        raceEntry.setCarNumber(car);
        return raceEntry;
    }

    private static Entry entry(long id, long competitorId, String transponder, EntryStatus status) {
        Entry entry = new Entry();
        entry.setId(id);
        entry.setCompetitorId(competitorId);
        entry.setTransponderNumberSnapshot(transponder);
        entry.setStatus(status);
        return entry;
    }
}
