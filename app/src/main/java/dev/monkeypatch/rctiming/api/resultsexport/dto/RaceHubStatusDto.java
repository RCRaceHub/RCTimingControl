package dev.monkeypatch.rctiming.api.resultsexport.dto;

import dev.monkeypatch.rctiming.resultsexport.RaceHubConnectionService;

import java.time.Instant;

/**
 * Whether the link to RaceHub is alive, for the race director's indicator.
 *
 * @param health        {@code NOT_CONFIGURED} (hide the indicator), {@code CHECKING}, {@code OK}, {@code UNREACHABLE},
 *                      {@code KEY_REFUSED} or {@code ERROR}
 * @param detail        what is wrong, or null when {@code OK}
 * @param checkedAt     when the link was last checked, or null
 * @param lastOkAt      when it last worked since the app started, or null
 * @param waiting       results exports not yet sent
 * @param failed        the part of {@code waiting} whose send failed
 * @param lastSentAt    when RaceHub last accepted results, or null
 * @param lastSendError why the last send failed, or null
 */
public record RaceHubStatusDto(String health, String detail, Instant checkedAt, Instant lastOkAt, int waiting,
                               int failed, Instant lastSentAt, String lastSendError) {

    public static RaceHubStatusDto from(RaceHubConnectionService.Status s) {
        return new RaceHubStatusDto(s.health().name(), s.detail(), s.checkedAt(), s.lastOkAt(), s.waiting(),
                s.failed(), s.lastSentAt(), s.lastSendError());
    }
}
