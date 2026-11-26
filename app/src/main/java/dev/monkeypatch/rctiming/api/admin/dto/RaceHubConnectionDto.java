package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/**
 * The club's RaceHub connection: the address and key that results sending and the RaceHub imports share.
 *
 * @param sendingEnabled  whether the RaceHub address and key are both set; without them, exports wait
 * @param resultsUrl      where results are sent, below the one RaceHub address, or null
 * @param raceHubUrl      RaceHub's address in use, or null
 * @param addressSource   where it comes from: {@code SAVED} (set on the RaceHub page), {@code SETTINGS_FILE},
 *                        {@code SEPARATE} (the older results and events addresses set one by one) or {@code NONE}
 * @param missingSettings what is still needed before anything is sent
 * @param keySource       where the key comes from: {@code SAVED} (set here), {@code SETTINGS_FILE} or {@code NONE}
 */
public record RaceHubConnectionDto(boolean sendingEnabled, String resultsUrl, String raceHubUrl,
                                   String addressSource, List<String> missingSettings, String keySource) {}
