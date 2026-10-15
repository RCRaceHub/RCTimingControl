package dev.monkeypatch.rctiming.config;

import dev.monkeypatch.rctiming.infrastructure.tts.TtsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the announcer's Piper settings ({@code rctiming.tts.*}) available to inject. It has no beans of its
 * own: {@code PiperTtsClient} is a component already.
 */
@Configuration
@EnableConfigurationProperties(TtsProperties.class)
public class TtsConfig {
    // TtsProperties is a @ConfigurationProperties record — just needs registration.
}
