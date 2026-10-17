package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.query.boards.BoardRaceDto;
import dev.monkeypatch.rctiming.service.AutoRunService.BoardView;

/**
 * What the now/next board shows for one event (L12). Any race may be
 * null: before the first heat only {@code nextRace} is set, after the last only
 * {@code lastCompletedRace}. {@code eventId} is the resolved event (see
 * {@code BoardQuery.resolveEvent}), so it is set between races too; it is null only when the
 * requested event doesn't exist, or none was requested and no event is racing or in progress.
 * {@code autoRun} is null unless automatic race progression is on for the event; the board counts down to
 * its {@code startAt}.
 */
public record NowNextDto(
        Long eventId,
        String eventName,
        BoardRaceDto currentRace,
        BoardRaceDto nextRace,
        BoardRaceDto lastCompletedRace,
        BoardView autoRun
) {}
