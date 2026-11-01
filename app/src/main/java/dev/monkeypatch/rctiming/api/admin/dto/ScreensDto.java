package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.screens.PendingScreens;

import java.time.Instant;
import java.util.List;

/**
 * The Screens page's list (#708): the known screens, the screens found on the network but not added yet, and whether
 * mDNS discovery is switched on.
 */
public record ScreensDto(boolean discoveryEnabled, List<ScreenDto> screens, List<Found> found) {

    /**
     * A screen heard on the network that an official can add. {@code authRequired} is what it said about itself, and
     * shows only that it asks for a token on changes.
     */
    public record Found(String screenId, String name, String host, int port, String agentVersion,
                        boolean authRequired, Instant seenAt) {

        public static Found from(PendingScreens.Found f) {
            return new Found(f.screenId(), f.name(), f.host(), f.port(), f.agentVersion(), f.authRequired(), f.seenAt());
        }
    }
}
