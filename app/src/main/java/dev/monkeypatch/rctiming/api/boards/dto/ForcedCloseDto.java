package dev.monkeypatch.rctiming.api.boards.dto;


/** Time left before a race is closed by force, in milliseconds; null while no limit is counting down. */
public record ForcedCloseDto(Long closesInMs) {}
