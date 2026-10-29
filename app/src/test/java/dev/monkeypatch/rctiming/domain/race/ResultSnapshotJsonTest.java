package dev.monkeypatch.rctiming.domain.race;

import dev.monkeypatch.rctiming.TestJson;
import org.junit.jupiter.api.Test;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultSnapshotJsonTest {

    private final ResultSnapshotJson json = new ResultSnapshotJson(TestJson.mapper());

    @Test
    void readsBackWhatItWrote() {
        var row = new ResultSnapshotDto.ResultRow(1, 7L, 3L, "Sam Driver", null, 12, 31_500L, 2_400L, null);

        List<ResultSnapshotDto.ResultRow> read = json.positions(5L, json.write(5L, List.of(row)));

        assertThat(read).containsExactly(row);
    }

    @Test
    void keepsTheDnfMarkAndReadsARowStoredWithoutOneAsFinished() {
        var dnf = new ResultSnapshotDto.ResultRow(2, 8L, 4L, "Pat Driver", null, 11, 0L, null, null).withDnf(true);
        String stored = "[{\"position\":1,\"entryId\":7,\"lapsCompleted\":12,\"totalTimeMs\":31500}]";

        assertThat(json.positions(5L, json.write(5L, List.of(dnf)))).containsExactly(dnf);
        assertThat(json.positions(5L, stored).get(0).dnf()).isFalse();
    }

    @Test
    void readsARowStoredBeforeRowsHadPointsAsOneWithNoPoints() {
        String stored = "[{\"position\":1,\"entryId\":7,\"competitorId\":3,\"driverName\":\"Sam Driver\","
                + "\"carNumber\":\"4\",\"lapsCompleted\":12,\"totalTimeMs\":31500,\"bestLapMs\":2400,"
                + "\"gapToLeaderMs\":null}]";

        List<ResultSnapshotDto.ResultRow> read = json.positions(5L, stored);

        assertThat(read).hasSize(1);
        assertThat(read.get(0).points()).isNull();
        assertThat(read.get(0).withPoints(3).points()).isEqualTo(3);
    }

    @Test
    void readsBackLapHistory() {
        var lap = new ResultSnapshotDto.PositionAtLap(3, 7L, 2, 31_000L);

        assertThat(json.lapHistory(5L, json.write(5L, List.of(lap)))).containsExactly(lap);
    }

    @Test
    void noStoredResultReadsAsNoRows() {
        assertThat(json.positions(5L, null)).isEmpty();
        assertThat(json.lapHistory(5L, null)).isEmpty();
        assertThat(json.positions(5L, "null")).isEmpty();
    }

    @Test
    void anUnreadableResultFailsNamingTheRace() {
        assertThatThrownBy(() -> json.positions(5L, "{\"not\": \"rows\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The stored result of race 5 could not be read");
    }
}
