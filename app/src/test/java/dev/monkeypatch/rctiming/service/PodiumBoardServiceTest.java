package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.race.RaceStatus;
import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto.ResultRow;
import dev.monkeypatch.rctiming.query.boards.FinalsBoardDto;
import dev.monkeypatch.rctiming.query.boards.PodiumBoardDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PodiumBoardServiceTest {

    private static ResultRow row(int pos, long competitor, String name) {
        return new ResultRow(pos, competitor, competitor, name, String.valueOf(pos), 20, 300_000L, 14_000L, null,
                null, null, false);
    }

    private static FinalsBoardDto.Final done(String letter, ResultRow... rows) {
        return new FinalsBoardDto.Final(letter, RaceStatus.FINISHED, false, List.of(rows), null);
    }

    private static FinalsBoardDto.Final pending(String letter) {
        return new FinalsBoardDto.Final(letter, RaceStatus.PENDING, false, List.of(), null);
    }

    @Test
    void thePodiumIsTheFirstThreeOfTheAFinalAndCategoryLeadersLookThroughEveryFinal() {
        var cls = new FinalsBoardDto.ClassFinals(1L, "1/10 Buggy", 2, 2, List.of(
                done("A", row(1, 1, "Ann"), row(2, 2, "Bob"), row(3, 3, "Cy"), row(4, 4, "Di")),
                done("B", row(1, 5, "Ed"), row(2, 6, "Flo"))));
        Map<Long, List<String>> tags = Map.of(
                3L, List.of("Vet"), 4L, List.of("Junior", "Vet"), 5L, List.of("junior"), 6L, List.of("Junior"));

        PodiumBoardDto.ClassPodium out = PodiumBoardService.build(List.of(cls), tags).get(0);

        assertThat(out.complete()).isTrue();
        assertThat(out.podium()).extracting(PodiumBoardDto.Place::driverName).containsExactly("Ann", "Bob", "Cy");
        assertThat(out.categoryLeaders()).extracting(PodiumBoardDto.CategoryLeader::category,
                        PodiumBoardDto.CategoryLeader::driverName, PodiumBoardDto.CategoryLeader::overallPosition)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Junior", "Di", 4),
                        org.assertj.core.groups.Tuple.tuple("Vet", "Cy", 3));
    }

    @Test
    void aClassWithNoFinishedFinalIsLeftOutAndAnUnfinishedAFinalHasNoPodium() {
        var notStarted = new FinalsBoardDto.ClassFinals(1L, "Stock", 0, 1, List.of(pending("A")));
        var bOnly = new FinalsBoardDto.ClassFinals(2L, "Modified", 1, 2, List.of(pending("A"), done("B", row(1, 9, "Gus"))));

        List<PodiumBoardDto.ClassPodium> out = PodiumBoardService.build(List.of(notStarted, bOnly), Map.of());

        assertThat(out).hasSize(1);
        assertThat(out.get(0).className()).isEqualTo("Modified");
        assertThat(out.get(0).complete()).isFalse();
        assertThat(out.get(0).podium()).isEmpty();
    }
}
