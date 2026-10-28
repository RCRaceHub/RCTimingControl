package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.Size;

/** The body of a save or an update: the template's name (on an update, left out to keep it). */
public record AudioTemplateRequest(@Size(max = 80) String name) {}
