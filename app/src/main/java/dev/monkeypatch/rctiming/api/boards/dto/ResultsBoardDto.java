package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.domain.race.ResultSnapshotDto;
import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.query.racecontrol.FinalLegsDto;

import java.util.List;

/**
 * The results board: the event's last finished race and its result rows (L12). {@code race}
 * is null, and {@code results} empty, until a race has finished. {@code eventId} follows the
 * same resolution as {@link NowNextDto}. {@code legs} is the combined result of the A final when the race is one of its
 * legs, else null.
 */
public record ResultsBoardDto(
        Long eventId,
        String eventName,
        BoardRaceDto race,
        List<ResultSnapshotDto.ResultRow> results,
        FinalLegsDto legs
) {}
