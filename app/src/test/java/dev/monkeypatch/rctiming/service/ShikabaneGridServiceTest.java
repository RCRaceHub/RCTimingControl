package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.format.EventClass;
import dev.monkeypatch.rctiming.domain.format.EventClassRepository;
import dev.monkeypatch.rctiming.domain.format.RaceFormatService;
import dev.monkeypatch.rctiming.domain.format.ShikabaneConfig;
import dev.monkeypatch.rctiming.domain.format.StartType;
import dev.monkeypatch.rctiming.domain.race.Race;
import dev.monkeypatch.rctiming.domain.race.RaceEntry;
import dev.monkeypatch.rctiming.domain.race.RaceEntryRepository;
import dev.monkeypatch.rctiming.domain.race.RaceRepository;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshot;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotJson;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotRepository;
import dev.monkeypatch.rctiming.domain.race.Round;
import dev.monkeypatch.rctiming.domain.race.RoundRepository;
import dev.monkeypatch.rctiming.domain.race.RoundType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Drawing the next Shikabane heat's grid: reverse after heat 1, cumulative points after that. */
class ShikabaneGridServiceTest {

    private static final ShikabaneConfig CONFIG = new ShikabaneConfig(5, StartType.GRID, 3, 3, 1);

    private final RoundRepository rounds = mock(RoundRepository.class);
    private final RaceRepository races = mock(RaceRepository.class);
    private final RaceEntryRepository raceEntries = mock(RaceEntryRepository.class);
    private final EventClassRepository eventClasses = mock(EventClassRepository.class);
    private final RaceFormatService formats = mock(RaceFormatService.class);
    private final ShikabaneHeatScores heatScores = mock(ShikabaneHeatScores.class);
    private final ResultSnapshotRepository snapshots = mock(ResultSnapshotRepository.class);
    private final ResultSnapshotJson json = mock(ResultSnapshotJson.class);
    private ShikabaneGridService service;

    private final Round round1 = round(11L, 1, 1);
    private final Round round2 = round(12L, 2, 2);
    private final Round round3 = round(13L, 3, 3);
    private final Race heat1 = heat(21L, 11L);
    private final Race heat2 = heat(22L, 12L);
    private final Race heat3 = heat(23L, 13L);

    @BeforeEach
    void setUp() {
        service = new ShikabaneGridService(rounds, races, raceEntries, eventClasses, formats, heatScores, snapshots,
                json);
        EventClass eventClass = new EventClass();
        when(eventClasses.findById(5L)).thenReturn(Optional.of(eventClass));
        when(formats.shikabaneConfig(eventClass)).thenReturn(Optional.of(CONFIG));
        when(rounds.findByEventIdOrderBySequenceInEvent(1L)).thenReturn(List.of(round1, round2, round3));
        when(rounds.findById(11L)).thenReturn(Optional.of(round1));
        when(rounds.findById(12L)).thenReturn(Optional.of(round2));
        when(races.findByRoundIdOrderBySequenceInRound(11L)).thenReturn(List.of(heat1));
        when(races.findByRoundIdOrderBySequenceInRound(12L)).thenReturn(List.of(heat2));
        when(races.findByRoundIdOrderBySequenceInRound(13L)).thenReturn(List.of(heat3));
        when(raceEntries.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void heatTwoStartsInTheReverseOfHeatOne() {
        ResultSnapshot snapshot = new ResultSnapshot();
        snapshot.setPositionsJson("r1");
        when(snapshots.findByRaceId(21L)).thenReturn(Optional.of(snapshot));
        when(json.positions(21L, "r1")).thenReturn(List.of(row(1, 101), row(2, 102), row(3, 103)));
        List<RaceEntry> grid = List.of(entry(101), entry(102), entry(103));
        when(raceEntries.findByRaceIdOrderByGridPosition(22L)).thenReturn(grid);

        service.applyAfterFinish(heat1);

        assertThat(grid).extracting(RaceEntry::getEntryId, RaceEntry::getGridPosition)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(103L, 1),
                        org.assertj.core.groups.Tuple.tuple(102L, 2),
                        org.assertj.core.groups.Tuple.tuple(101L, 3));
    }

    @Test
    void heatThreeStartsByCumulativePointsFewestFirst() {
        when(heatScores.scores(heat1, CONFIG)).thenReturn(List.of(
                new ShikabaneHeatScores.Score(101, 1, 10), new ShikabaneHeatScores.Score(102, 2, 9),
                new ShikabaneHeatScores.Score(103, 3, 8)));
        when(heatScores.scores(heat2, CONFIG)).thenReturn(List.of(
                new ShikabaneHeatScores.Score(101, 3, 5), new ShikabaneHeatScores.Score(102, 1, 12),
                new ShikabaneHeatScores.Score(103, 2, 7)));
        List<RaceEntry> grid = List.of(entry(101), entry(102), entry(103));
        when(raceEntries.findByRaceIdOrderByGridPosition(23L)).thenReturn(grid);

        service.applyAfterFinish(heat2);

        // 101: 4, 102: 3, 103: 5
        assertThat(grid).extracting(RaceEntry::getEntryId, RaceEntry::getGridPosition)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(102L, 1),
                        org.assertj.core.groups.Tuple.tuple(101L, 2),
                        org.assertj.core.groups.Tuple.tuple(103L, 3));
    }

    private static Round round(long id, int number, int sequence) {
        Round r = new Round();
        r.setId(id);
        r.setEventId(1L);
        r.setType(RoundType.QUALIFIER);
        r.setRoundNumber(number);
        r.setSequenceInEvent(sequence);
        return r;
    }

    private static Race heat(long id, long roundId) {
        Race race = new Race();
        race.setId(id);
        race.setRoundId(roundId);
        race.setEventClassId(5L);
        race.setHeatNumber(1);
        return race;
    }

    private static RaceEntry entry(long entryId) {
        RaceEntry e = new RaceEntry();
        e.setEntryId(entryId);
        return e;
    }

    private static ResultRow row(int position, long entryId) {
        return new ResultRow(position, entryId, null, "D", "1", 5, 1L, 1L, null);
    }
}
