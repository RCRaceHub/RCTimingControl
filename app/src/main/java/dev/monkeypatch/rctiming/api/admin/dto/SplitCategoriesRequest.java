package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The body of {@code PUT /{id}/split-categories}: the tags to split the standings by; empty for none. */
public record SplitCategoriesRequest(@NotNull List<String> categories) {}
