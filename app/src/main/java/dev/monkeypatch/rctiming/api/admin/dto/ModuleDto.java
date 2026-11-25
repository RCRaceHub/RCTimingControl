package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.modules.ModuleSwitch.ModuleStatus;

/**
 * A module as the Modules page and the menu see it (#797).
 *
 * @param enabled    usable now: on for the club and, when the answer is for an event, not turned off by it
 * @param clubEnabled the club-wide switch
 * @param eventOff   the event turned it off (false when the answer is not about an event)
 */
public record ModuleDto(String id, String name, String description, String version, boolean enabled,
                        boolean clubEnabled, boolean eventOff) {

    public static ModuleDto from(ModuleStatus s) {
        return new ModuleDto(s.module().id(), s.module().name(), s.module().description(), s.module().version(),
                s.enabled(), s.clubEnabled(), s.eventOff());
    }
}
