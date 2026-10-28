package dev.monkeypatch.rctiming.api.admin.dto;

/** The body of {@code PUT /{id}/spoken-name}: how the name is said aloud. Null or blank clears it. */
public record SpokenNameRequest(String spokenName) {}
