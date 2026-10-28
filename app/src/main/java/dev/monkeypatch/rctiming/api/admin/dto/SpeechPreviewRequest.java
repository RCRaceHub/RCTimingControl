package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.competitor.CompetitorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /spoken-name/preview}: the text to hear in the club's voice, required. */
public record SpeechPreviewRequest(
        @NotBlank @Size(max = CompetitorService.MAX_SPOKEN_NAME_LENGTH) String text) {}
