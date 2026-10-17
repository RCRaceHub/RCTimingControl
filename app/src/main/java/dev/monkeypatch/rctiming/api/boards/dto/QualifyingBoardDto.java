package dev.monkeypatch.rctiming.api.boards.dto;

import dev.monkeypatch.rctiming.query.racecontrol.QualifyingTableDto;

import java.util.List;

/**
 * The overall qualifying order of each class of an event, for anyone on the venue network. Names are as the
 * public may see them (#265). Answer of {@code GET /api/v1/boards/qualifying}; {@code eventId} and
 * {@code eventName} are null, and {@code tables} empty, when no event is found.
 */
public record QualifyingBoardDto(Long eventId, String eventName, List<QualifyingTableDto> tables) {}
