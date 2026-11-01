package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/** The Screens page's list (#708): the known screens and whether mDNS discovery is switched on. */
public record ScreensDto(boolean discoveryEnabled, List<ScreenDto> screens) {
}
