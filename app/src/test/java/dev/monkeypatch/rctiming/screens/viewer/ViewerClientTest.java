package dev.monkeypatch.rctiming.screens.viewer;

import dev.monkeypatch.rctiming.screens.FakeScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The viewer API client (#708) against a fake agent built from the payloads in the contract. */
class ViewerClientTest {

    private final ViewerClient client = new ViewerClient(RestClient.builder(), JsonMapper.builder().build());
    private FakeScreen screen;

    @BeforeEach
    void startScreen() {
        screen = new FakeScreen("screen-01", "secret");
    }

    @AfterEach
    void stopScreen() {
        screen.close();
    }

    private ViewerTarget target(String token) {
        return new ViewerTarget("127.0.0.1", screen.port(), token);
    }

    @Test
    void infoAndStatusAreReadAndUnknownFieldsAreIgnored() {
        ViewerInfo info = client.info(target(null));
        ViewerStatus status = client.status(target(null));

        assertThat(info.id()).isEqualTo("screen-01");
        assertThat(info.apiVersion()).isEqualTo("1");
        assertThat(info.authRequired()).isTrue();
        assertThat(info.limits().minDuration()).isEqualTo(5);
        assertThat(status.state()).isEqualTo("idle");
        assertThat(status.browser().running()).isTrue();
        assertThat(status.network().pageReachable()).isTrue();
        assertThat(status.playlist().revision()).isEqualTo(1);
    }

    @Test
    void readsNeverSendTheTokenButChangesDo() {
        client.status(target("secret"));
        ViewerPlaylist.Body body = new ViewerPlaylist.Body(
                List.of(new ViewerPlaylist.Item("http://rctc.local/boards/results", 20, "Results", "results")), 30, 0,
                "rctc:playlist-1");

        ViewerPlaylist saved = client.putPlaylist(target("secret"), body);

        assertThat(saved.revision()).isEqualTo(2);
        assertThat(saved.source()).isEqualTo("rctc:playlist-1");
        assertThat(screen.calls()).containsExactly("GET /status", "PUT /playlist (auth)");
        assertThat(screen.lastBody()).contains("\"default_duration\":30").contains("\"start_index\":0");
    }

    @Test
    void aWrongTokenIsA401WithTheAgentsMessage() {
        assertThatThrownBy(() -> client.control(target("wrong"), "next"))
                .isInstanceOfSatisfying(ViewerException.class, e -> {
                    assertThat(e.httpStatus()).isEqualTo(401);
                    assertThat(e.getMessage()).isEqualTo("missing or wrong token");
                    assertThat(e.unreachable()).isFalse();
                });
    }

    @Test
    void aBadFieldIsA400WithTheAgentsMessage() {
        assertThatThrownBy(() -> client.goTo(target("secret"), 99))
                .isInstanceOfSatisfying(ViewerException.class, e -> {
                    assertThat(e.httpStatus()).isEqualTo(400);
                    assertThat(e.getMessage()).isEqualTo("index out of range");
                });
    }

    @Test
    void aTemporaryPageCanBeShownAndCleared() {
        ViewerStatus shown = client.show(target("secret"), "http://rctc.local/boards/top", 30, "Podium");
        ViewerStatus cleared = client.clearShow(target("secret"));

        assertThat(shown.state()).isEqualTo("override");
        assertThat(shown.override().url()).isEqualTo("http://rctc.local/boards/top");
        assertThat(cleared.state()).isEqualTo("idle");
    }

    @Test
    void aScreenThatIsOffIsUnreachable() {
        int port = screen.port();
        screen.close();

        assertThatThrownBy(() -> client.status(new ViewerTarget("127.0.0.1", port, null)))
                .isInstanceOfSatisfying(ViewerException.class, e -> assertThat(e.unreachable()).isTrue());
    }
}
