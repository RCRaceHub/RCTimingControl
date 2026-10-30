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

    private RaceHubResultsEndpoint withAccess(String configUrl, String id, String secret) {
        return new RaceHubResultsEndpoint(new RaceHubResultsProperties(
                configUrl == null ? null : URI.create(configUrl), "key", id, secret), profile);
    }

    @Test
    void cloudflareAccessHeadersGoToRaceHubsResultsHostAndSavedEventsHostOnly() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        when(profile.getRaceHubEventsUrl()).thenReturn(Optional.of("https://events.example/api/rctc/v1/events"));
        RaceHubResultsEndpoint endpoint = withAccess("https://racehub.example/api/results", "client-id", "client-secret");

        assertThat(endpoint.accessHeaders(URI.create("https://racehub.example/api/results")))
                .containsEntry("CF-Access-Client-Id", "client-id")
                .containsEntry("CF-Access-Client-Secret", "client-secret");
        assertThat(endpoint.accessHeaders(URI.create("https://events.example/api/rctc/v1/events/7/entries")))
                .containsKey("CF-Access-Client-Id");
        assertThat(endpoint.headersFor(URI.create("https://racehub.example/x"))).hasSize(2);
        // A feed on any other host, another port or plain http never gets the secret
        assertThat(endpoint.accessHeaders(URI.create("https://other-booking.example/entries"))).isEmpty();
        assertThat(endpoint.accessHeaders(URI.create("https://racehub.example:8443/x"))).isEmpty();
        assertThat(endpoint.accessHeaders(URI.create("http://racehub.example/x"))).isEmpty();
        assertThat(endpoint.accessHeaders(null)).isEmpty();
    }

    @Test
    void noCloudflareAccessSettingsMeansNoExtraHeaders() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        when(profile.getRaceHubEventsUrl()).thenReturn(Optional.empty());

        assertThat(endpoint("https://racehub.example/api/results", "key")
                .accessHeaders(URI.create("https://racehub.example/api/results"))).isEmpty();
    }

    @Test
    void anUnreadableSavedEventsAddressMatchesNothing() {
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        when(profile.getRaceHubEventsUrl()).thenReturn(Optional.of("ht tp://bad address"));

        assertThat(withAccess("https://racehub.example/api/results", "id", "secret")
                .accessHeaders(URI.create("https://elsewhere.example/x"))).isEmpty();
    }
}
