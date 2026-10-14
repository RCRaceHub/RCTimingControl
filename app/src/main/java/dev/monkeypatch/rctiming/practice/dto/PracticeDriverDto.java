package dev.monkeypatch.rctiming.practice.dto;

/** A transponder named in a practice session. */
public record PracticeDriverDto(String transponderNumber, Long competitorId, String displayName) {}
