package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubMeetingService.Meeting;

/** The event an Entry Export file belongs to, and whether the request created it. */
public record RaceHubMeetingDto(Long eventId, String name, boolean created) {

    public static RaceHubMeetingDto from(Meeting meeting) {
        return new RaceHubMeetingDto(meeting.event().getId(), meeting.event().getName(), meeting.created());
    }
}
