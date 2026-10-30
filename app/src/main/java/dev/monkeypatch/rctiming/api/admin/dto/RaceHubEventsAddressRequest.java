package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

/** @param eventsUrl the address RaceHub lists ready events at; blank clears it */
public record RaceHubEventsAddressRequest(@Size(max = 500) String eventsUrl) {}
