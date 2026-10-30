package dev.monkeypatch.rctiming.resultsexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RaceHubResultsPropertiesTest {

    @Test
    void sendsOnlyWithBothTheAddressAndTheKey() {
        assertThat(new RaceHubResultsProperties(URI.create("https://racehub.example/api/results"), "key")
                .sendingEnabled()).isTrue();

        var noKey = new RaceHubResultsProperties(URI.create("https://racehub.example/api/results"), " ");
        assertThat(noKey.sendingEnabled()).isFalse();
        assertThat(noKey.missingSettings()).containsExactly("rctiming.racehub.token");

        var nothing = new RaceHubResultsProperties(null, null);
        assertThat(nothing.sendingEnabled()).isFalse();
        assertThat(nothing.missingSettings()).containsExactly("rctiming.racehub.results-url", "rctiming.racehub.token");
    }

    @Test
    void refusesPlainHttpToAnotherMachine() {
        assertThatThrownBy(() -> new RaceHubResultsProperties(URI.create("http://racehub.example/api/results"), "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThatThrownBy(() -> new RaceHubResultsProperties(URI.create("ftp://racehub.example/results"), "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8099/results", "http://127.0.0.1:8099/results", "http://[::1]:8099/results"})
    void allowsPlainHttpToThisMachineForTesting(String url) {
        assertThat(new RaceHubResultsProperties(URI.create(url), "key").sendingEnabled()).isTrue();
    }

    @Test
    void cloudflareAccessIsOptionalButAllOrNothing() {
        assertThat(new RaceHubResultsProperties(URI.create("https://racehub.example/r"), "key").accessConfigured())
                .isFalse();
        assertThat(new RaceHubResultsProperties(URI.create("https://racehub.example/r"), "key", " ", "")
                .accessConfigured()).isFalse();
        assertThat(new RaceHubResultsProperties(URI.create("https://racehub.example/r"), "key", "id", "secret")
                .accessConfigured()).isTrue();

        assertThatThrownBy(() -> new RaceHubResultsProperties(null, "key", "id", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access-client-id").hasMessageContaining("access-client-secret")
                // The secret itself is never in a message
                .hasMessageNotContaining("secret-value");
        assertThatThrownBy(() -> new RaceHubResultsProperties(null, "key", null, "secret-value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret-value");
    }
}
