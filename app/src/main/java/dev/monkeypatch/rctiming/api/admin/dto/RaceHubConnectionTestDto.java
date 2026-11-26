package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.resultsexport.RaceHubConnectionService;

import java.time.Instant;
import java.util.List;

/**
 * The result of the admin's "Test connection" for RaceHub.
 *
 * @param ok          whether every check passed
 * @param summary     one sentence to show first: that it works, or the first thing wrong
 * @param checkedAt   when the test ran
 * @param readyEvents how many meetings RaceHub lists as released to timing, or null when not read
 * @param checks      each check in order: address, key, reach, read, write
 */
public record RaceHubConnectionTestDto(boolean ok, String summary, Instant checkedAt, Integer readyEvents,
                                       List<CheckDto> checks) {

    /**
     * One check.
     *
     * @param id     {@code address}, {@code key}, {@code reach}, {@code read} or {@code write}
     * @param state  {@code PASS}, {@code FAIL}, {@code UNKNOWN} or {@code SKIPPED}
     * @param detail what happened, and what to do about it
     */
    public record CheckDto(String id, String label, String state, String detail) {
    }

    public static RaceHubConnectionTestDto from(RaceHubConnectionService.TestResult result) {
        return new RaceHubConnectionTestDto(result.ok(), result.summary(), result.checkedAt(), result.readyEvents(),
                result.checks().stream()
                        .map(c -> new CheckDto(c.id(), c.label(), c.state().name(), c.detail()))
                        .toList());
    }
}
