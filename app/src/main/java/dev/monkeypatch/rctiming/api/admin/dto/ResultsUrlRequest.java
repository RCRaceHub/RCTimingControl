package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

/** @param resultsUrl the RaceHub address; blank clears it, so the settings file's applies */
public record ResultsUrlRequest(@Size(max = 500) String resultsUrl) {}
