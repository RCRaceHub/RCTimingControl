package dev.monkeypatch.rctiming.api.setup.dto;


/**
 * The name in the settings file (a bare host label, empty when there is none), the name actually being announced
 * (such as {@code rctiming.local}, empty until an interface has taken it), the suggestion, and whether the file
 * and the running app disagree so a restart is needed. {@code overriddenByEnvironment} is true when the
 * {@code RCTIMING_NETWORK_MDNS_NAME} environment variable (as set in Docker) decides the name, so the file has
 * no effect.
 */
public record NetworkNameDto(String configuredName, String announcedHost, String suggestedName,
                             boolean restartNeeded, boolean overriddenByEnvironment) {
}
