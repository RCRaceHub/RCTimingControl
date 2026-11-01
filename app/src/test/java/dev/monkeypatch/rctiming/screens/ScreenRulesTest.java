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
}
