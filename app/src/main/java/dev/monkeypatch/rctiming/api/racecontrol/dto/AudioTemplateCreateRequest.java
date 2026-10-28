package dev.monkeypatch.rctiming.api.racecontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of a save: the template's name. */
public record AudioTemplateCreateRequest(@NotBlank @Size(max = 80) String name) {}
