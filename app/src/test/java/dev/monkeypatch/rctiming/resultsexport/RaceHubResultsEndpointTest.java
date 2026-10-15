package dev.monkeypatch.rctiming.resultsexport;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaceHubResultsEndpointTest {

    private final ClubProfileService profile = mock(ClubProfileService.class);

    private RaceHubResultsEndpoint endpoint(String configUrl, String token) {
        return new RaceHubResultsEndpoint(
                new RaceHubResultsProperties(configUrl == null ? null : URI.create(configUrl), token), profile);
    }

    @Test
    void theAddressSetInTheAdminPanelWinsOverTheSettingsFile() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.of("https://panel.example/results"));
        RaceHubResultsEndpoint endpoint = endpoint("https://file.example/results", "key");

        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://panel.example/results"));
        assertThat(endpoint.snapshot().savedUrl()).isEqualTo("https://panel.example/results");
    }

    @Test
    void anEmptyAdminSettingFallsBackToTheSettingsFile() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        RaceHubResultsEndpoint endpoint = endpoint("https://file.example/results", "key");

        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://file.example/results"));
        assertThat(endpoint.snapshot().savedUrl()).isNull();
        assertThat(endpoint.snapshot().sendingEnabled()).isTrue();
    }

    @Test
    void sendingNeedsAnAddressFromEitherPlaceAndTheKey() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        assertThat(endpoint(null, null).snapshot().missingSettings())
                .containsExactly("the RaceHub address", "rctiming.racehub.token");

        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.of("https://panel.example/results"));
        assertThat(endpoint(null, "key").snapshot().sendingEnabled()).isTrue();
        assertThat(endpoint(null, null).snapshot().missingSettings()).containsExactly("rctiming.racehub.token");
    }

    @Test
    void aSavedAddressThatIsNoLongerValidFallsBackToTheSettingsFile() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.of("http://racehub.example/results"));
        var snapshot = endpoint("https://file.example/results", "key").snapshot();

        assertThat(snapshot.resultsUrl()).isEqualTo(URI.create("https://file.example/results"));
        assertThat(snapshot.savedUrl()).isNull();
    }

    @Test
    void parseAcceptsHttpsAndLoopbackHttpOnly() {
        assertThat(RaceHubResultsEndpoint.parse(" https://racehub.example/api/results ").getHost())
                .isEqualTo("racehub.example");
        assertThat(RaceHubResultsEndpoint.parse("http://localhost:8099/results")).isNotNull();
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parse("http://racehub.example/results"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parse("https://user:secret@racehub.example/results"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parse("not an address"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parse("racehub.example/results"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
