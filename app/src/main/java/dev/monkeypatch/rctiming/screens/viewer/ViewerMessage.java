package dev.monkeypatch.rctiming.screens.viewer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A full-screen message as a screen reports it (agent 0.6.0+, {@code GET /status} and the message endpoints). Times
 * are the screen's own clock in Unix seconds; {@code until} is null for a message that stays until cleared.
 *
 * @param style {@code hold} or {@code announce}
 * @param level {@code info}, {@code warning} or {@code urgent}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViewerMessage(String style, String title, String text, String level, Double since, Double until) {

    /**
     * The body of {@code PUT /api/v1/message}. Fields left null are left out so the screen applies its own defaults
     * ({@code hold}, {@code info}, until cleared).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String title, String text, String style, String level, Integer duration) {
    }

    /** The answer of every message endpoint: {@code {"message": {...} | null}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(ViewerMessage message) {
    }
}
