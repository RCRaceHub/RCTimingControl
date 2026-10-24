package dev.monkeypatch.rctiming.timing.battle;

import java.util.List;

/**
 * One battle, or a train of cars each close to the next, as sent on {@code /topic/race/{raceId}/battles} and
 * returned by {@code GET /api/v1/boards/races/{raceId}/battles} (#505). {@code cars} run from the front of the group
 * to the back; {@code links} holds the gap between each car and the one behind it, so it has one entry fewer. A
 * group of two is a battle for the place of the first car; three or more is a train. {@code gainingFast} is true if
 * any link is.
 */
public record BattleGroupDto(List<BattleCarDto> cars, List<BattleLinkDto> links, boolean gainingFast) {
}
