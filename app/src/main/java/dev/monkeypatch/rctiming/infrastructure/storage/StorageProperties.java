package dev.monkeypatch.rctiming.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code storage.*}: where uploads (the club logo, announcer clips) are kept and how browsers fetch them.
 *
 * @param localPath     folder for uploads; empty uses an {@code uploads} folder beside the database
 * @param publicBaseUrl what the URL of an upload starts with; relative by default, so logos load on whichever address
 *                      a phone or board opened the app with
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @DefaultValue("") String localPath,
        @DefaultValue("/storage") String publicBaseUrl) {
}
