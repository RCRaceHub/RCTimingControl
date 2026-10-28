package dev.monkeypatch.rctiming.resultsexport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code rctiming.results-site.*}: the folder the results site is written to for clubs without RaceHub (#359).
 *
 * @param directory      the folder to write the site into; empty writes nothing
 * @param refreshMinutes the longest the site is left before it is written again even if nothing finished, at
 *                       least 1
 */
@ConfigurationProperties(prefix = "rctiming.results-site")
public record ResultsSiteProperties(
        @DefaultValue("") String directory,
        @DefaultValue("5") long refreshMinutes) {
}
