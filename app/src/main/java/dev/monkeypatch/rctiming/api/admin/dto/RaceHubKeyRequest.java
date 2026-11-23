package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param key the key RaceHub issued for the club; it is never returned by the API */
public record RaceHubKeyRequest(@NotBlank @Size(max = 500) String key) {}
