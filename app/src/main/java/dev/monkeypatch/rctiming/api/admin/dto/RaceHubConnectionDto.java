package dev.monkeypatch.rctiming.api.admin.dto;

import java.util.List;

/**
 * The club's RaceHub connection: the address and key that results sending and the RaceHub imports share.
 *
 * @param sendingEnabled  whether the RaceHub address and key are both set; without them, exports wait
 * @param resultsUrl      the address in use, or null
 * @param savedUrl        the address set here, or null when the settings file's is in use
 * @param missingSettings what is still needed before anything is sent
 * @param keySource       where the key comes from: {@code SAVED} (set here), {@code SETTINGS_FILE} or {@code NONE}
 */
public record RaceHubConnectionDto(boolean sendingEnabled, String resultsUrl, String savedUrl,
                                   List<String> missingSettings, String keySource) {}
