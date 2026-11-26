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

    private RaceHubResultsEndpoint endpointWithUrl(String fileUrl, String token) {
        return new RaceHubResultsEndpoint(new RaceHubResultsProperties(null, token, null, null,
                fileUrl == null ? null : URI.create(fileUrl)), profile);
    }

    @Test
    void theAddressSavedInTheAdminPanelWinsOverTheSettingsFile() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.of("https://panel.example"));
        RaceHubResultsEndpoint endpoint = endpointWithUrl("https://file.example", "key");

        var snapshot = endpoint.snapshot();
        assertThat(snapshot.baseUrl()).isEqualTo(URI.create("https://panel.example"));
        assertThat(snapshot.savedBase()).isEqualTo("https://panel.example");
        assertThat(snapshot.source()).isEqualTo(RaceHubResultsEndpoint.AddressSource.SAVED);
        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://panel.example/api/rctc/v1/results"));
        assertThat(endpoint.eventsUrl()).isEqualTo(URI.create("https://panel.example/api/rctc/v1/events?state=ready"));
    }

    @Test
    void anEmptyAdminSettingFallsBackToTheSettingsFile() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.empty());
        RaceHubResultsEndpoint endpoint = endpointWithUrl("https://file.example/", "key");

        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://file.example/api/rctc/v1/results"));
        assertThat(endpoint.snapshot().savedBase()).isNull();
        assertThat(endpoint.snapshot().source()).isEqualTo(RaceHubResultsEndpoint.AddressSource.SETTINGS_FILE);
        assertThat(endpoint.snapshot().sendingEnabled()).isTrue();
    }

    @Test
    void oneAddressServesResultsEventsAndRaceHubsOwnHost() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.of("https://racehub.example"));
        RaceHubResultsEndpoint endpoint = endpoint(null, "key");

        assertThat(endpoint.isRaceHub(URI.create("https://racehub.example/api/rctc/v1/events/7/entries"))).isTrue();
        assertThat(endpoint.isRaceHub(URI.create("https://elsewhere.example/entries"))).isFalse();
    }

    @Test
    void theOlderSeparateAddressesApplyOnlyWhileNoRaceHubAddressIsSet() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.empty());
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.of("https://panel.example/results"));
        when(profile.getRaceHubEventsUrl()).thenReturn(Optional.of("https://panel.example/api/rctc/v1/events"));
        RaceHubResultsEndpoint endpoint = endpoint("https://file.example/results", "key");

        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://panel.example/results"));
        assertThat(endpoint.eventsUrl()).isEqualTo(URI.create("https://panel.example/api/rctc/v1/events"));
        assertThat(endpoint.snapshot().source()).isEqualTo(RaceHubResultsEndpoint.AddressSource.SEPARATE);
        assertThat(endpoint.snapshot().baseUrl()).isNull();

        when(profile.getRaceHubUrl()).thenReturn(Optional.of("https://one.example"));
        assertThat(endpoint.resultsUrl()).isEqualTo(URI.create("https://one.example/api/rctc/v1/results"));
    }

    @Test
    void sendingNeedsAnAddressFromEitherPlaceAndTheKey() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.empty());
        when(profile.getRaceHubResultsUrl()).thenReturn(Optional.empty());
        assertThat(endpoint(null, null).snapshot().missingSettings())
                .containsExactly("the RaceHub address", "rctiming.racehub.token");

        when(profile.getRaceHubUrl()).thenReturn(Optional.of("https://panel.example"));
        assertThat(endpoint(null, "key").snapshot().sendingEnabled()).isTrue();
        assertThat(endpoint(null, null).snapshot().missingSettings()).containsExactly("rctiming.racehub.token");
    }

    @Test
    void aSavedAddressThatIsNoLongerValidFallsBackToTheSettingsFile() {
        when(profile.getRaceHubUrl()).thenReturn(Optional.of("http://racehub.example"));
        var snapshot = endpointWithUrl("https://file.example", "key").snapshot();

        assertThat(snapshot.resultsUrl()).isEqualTo(URI.create("https://file.example/api/rctc/v1/results"));
        assertThat(snapshot.savedBase()).isNull();
    }

    @Test
    void parseBaseReducesAnyAddressOnRaceHubToItsHost() {
        assertThat(RaceHubResultsEndpoint.parseBase(" https://racehub.example/ "))
                .isEqualTo(URI.create("https://racehub.example"));
        assertThat(RaceHubResultsEndpoint.parseBase("https://racehub.example/api/rctc/v1/events?state=ready"))
                .isEqualTo(URI.create("https://racehub.example"));
        assertThat(RaceHubResultsEndpoint.parseBase("https://club.example:8443/racehub/api/rctc/v1/results"))
                .isEqualTo(URI.create("https://club.example:8443/racehub"));
        assertThat(RaceHubResultsEndpoint.parseBase("http://localhost:8099")).isNotNull();
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parseBase("http://racehub.example"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https");
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parseBase("https://user:secret@racehub.example"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        assertThatThrownBy(() -> RaceHubResultsEndpoint.parseBase("racehub.example"))
                .isInstanceOf(IllegalArgumentException.class);
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
