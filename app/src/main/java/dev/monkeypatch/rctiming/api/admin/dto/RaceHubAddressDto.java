package dev.monkeypatch.rctiming.api.admin.dto;

import dev.monkeypatch.rctiming.resultsexport.RaceHubResultsEndpoint;

/**
 * The one RaceHub address, as the RaceHub page and the pickers show it.
 *
 * @param address    RaceHub's address in use, or null when none is set
 * @param savedAddress the address saved in the admin panel, or null when the settings file's is in use
 * @param source     where the address in use comes from: {@code SAVED}, {@code SETTINGS_FILE}, {@code SEPARATE} (the
 *                   older results and events addresses set one by one) or {@code NONE}
 * @param resultsUrl where results are sent, or null
 */
public record RaceHubAddressDto(String address, String savedAddress, String source, String resultsUrl) {

    public static RaceHubAddressDto from(RaceHubResultsEndpoint.Snapshot snapshot) {
        return new RaceHubAddressDto(snapshot.baseUrl() == null ? null : snapshot.baseUrl().toString(),
                snapshot.savedBase(), snapshot.source().name(),
                snapshot.resultsUrl() == null ? null : snapshot.resultsUrl().toString());
    }
}
