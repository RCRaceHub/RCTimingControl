package dev.monkeypatch.rctiming.infrastructure.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * {@code tts.*}: the announcer's Piper text-to-speech server.
 *
 * @param endpoint     {@code host:port} of Piper's Wyoming interface; the port defaults to 10200
 * @param defaultVoice Piper voice name used when the club has chosen none
 * @param enabled      false turns speech off entirely; the browsers then use their own voices
 * @param locales      locale prefixes (such as {@code en_GB}) of the voices to offer; see {@link #effectiveLocales()}
 */
@ConfigurationProperties(prefix = "tts")
public record TtsProperties(String endpoint, String defaultVoice, boolean enabled, List<String> locales) {

    /** Effective locale list: configured values, or a single entry derived from the JVM default locale. */
    public List<String> effectiveLocales() {
        if (locales != null && !locales.isEmpty()) {
            return locales;
        }
        Locale jvmLocale = Locale.getDefault();
        String piperLocale = jvmLocale.getLanguage() + "_" + jvmLocale.getCountry().toUpperCase();
        return List.of(piperLocale);
    }
}
