package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

/** @param address RaceHub's address, such as {@code https://racehub.example}; blank clears it, so the settings file's applies */
public record RaceHubAddressRequest(@Size(max = 500) String address) {}
