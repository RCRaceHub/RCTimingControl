package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** The body of a sponsors change: up to five names. */
public record SponsorsRequest(@Size(max = 5) List<String> names) {}
