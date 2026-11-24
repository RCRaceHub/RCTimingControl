package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.domain.racehub.RaceHubClassMapping;

/** One mapping, as sent and returned: a RaceHub event class id and the event class it lands in. */
public record ClassMappingDto(String racehubEventClassId, Long eventClassId) {
    public static ClassMappingDto of(RaceHubClassMapping m) {
        return new ClassMappingDto(m.getRacehubEventClassId(), m.getEventClassId());
    }
}
