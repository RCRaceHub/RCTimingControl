package dev.monkeypatch.rctiming.screens;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What an official may type into the screen manager (#708). */
class ScreenRulesTest {

    @Test
    void aFullHttpAddressIsAccepted() {
        assertThat(ScreenRules.checkWebUrl(" http://192.168.1.5:8080/boards/results ", "Page"))
                .isEqualTo("http://192.168.1.5:8080/boards/results");
        assertThat(ScreenRules.checkWebUrl("https://timing.local/", "Page")).isEqualTo("https://timing.local/");
    }

    @Test
    void anAddressWithoutASchemeOrHostIsRefused() {
        assertThatThrownBy(() -> ScreenRules.checkWebUrl("timing.local/boards", "Page"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Page");
        assertThatThrownBy(() -> ScreenRules.checkWebUrl("ftp://timing.local/", "Page"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenRules.checkWebUrl("http://", "Page")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenRules.checkWebUrl("", "Page")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPlaylistNeedsPagesWithSensibleTimes() {
        PlaylistItem good = new PlaylistItem("http://a.local/x", 20, " Live ", null);

        assertThat(ScreenRules.checkItems(List.of(good))).containsExactly(new PlaylistItem("http://a.local/x", 20, "Live", null));
        assertThatThrownBy(() -> ScreenRules.checkItems(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenRules.checkItems(List.of(new PlaylistItem("http://a.local/x", 2, null, null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Page 1");
        assertThatThrownBy(() -> ScreenRules.checkDefaultDuration(1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHostIsANameOrAddressOnly() {
        assertThat(ScreenRules.checkHost("screen-01.local")).isEqualTo("screen-01.local");
        assertThat(ScreenRules.checkHost("192.168.1.20")).isEqualTo("192.168.1.20");
        assertThatThrownBy(() -> ScreenRules.checkHost("http://pi")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenRules.checkHost("pi:8080")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenRules.checkHost("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMessageNeedsATitleAndIsCleanedAndLowerCased() {
        var body = ScreenRules.checkMessage(new ScreenMessage("  Finals being calculated ", " Lists soon ", " HOLD", "Warning", 30));

        assertThat(body.title()).isEqualTo("Finals being calculated");
        assertThat(body.text()).isEqualTo("Lists soon");
        assertThat(body.style()).isEqualTo("hold");
        assertThat(body.level()).isEqualTo("warning");
        assertThat(body.duration()).isEqualTo(30);
        var plain = ScreenRules.checkMessage(new ScreenMessage("Hi", " ", null, null, null));
        assertThat(plain.text()).isNull();
        assertThat(plain.style()).isNull();
        assertThat(plain.duration()).isNull();
    }

    @Test
    void aMessageTheScreensWouldRefuseIsRefusedHere() {
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage(" ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage("x".repeat(121), null, null, null, null)))
                .hasMessageContaining("120");
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage("Hi", "y".repeat(501), null, null, null)))
                .hasMessageContaining("500");
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage("Hi", null, "shout", null, null)))
                .hasMessageContaining("style");
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage("Hi", null, null, "purple", null)))
                .hasMessageContaining("level");
        assertThatThrownBy(() -> ScreenRules.checkMessage(new ScreenMessage("Hi", null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ScreenRules.checkMessage(new ScreenMessage("x".repeat(120), "y".repeat(500), null, null, null)).title())
                .hasSize(120);
    }

    @Test
    void aBannerNeedsTextOfAtMost500Characters() {
        assertThat(ScreenRules.checkBanner(new ScreenBanner(" Pits close at 5 ", "urgent", 60)).text()).isEqualTo("Pits close at 5");
        assertThatThrownBy(() -> ScreenRules.checkBanner(new ScreenBanner(" ", null, null))).hasMessageContaining("text");
        assertThatThrownBy(() -> ScreenRules.checkBanner(new ScreenBanner("z".repeat(501), null, null))).hasMessageContaining("500");
        assertThatThrownBy(() -> ScreenRules.checkBanner(new ScreenBanner("Hi", "blue", null))).hasMessageContaining("level");
    }
}
