package dev.monkeypatch.rctiming.api.admin.dto;

import jakarta.validation.constraints.Size;

/** The body of {@code PUT /privacy-notice}. {@code text} blank means go back to the default notice. */
public record PrivacyNoticeRequest(@Size(max = 20000) String text) {}
