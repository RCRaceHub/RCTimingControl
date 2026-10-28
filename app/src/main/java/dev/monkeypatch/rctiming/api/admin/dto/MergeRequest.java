package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /merge}: the competitor to keep and the duplicate to fold into it. */
public record MergeRequest(@NotNull Long keepId, @NotNull Long duplicateId) {}
