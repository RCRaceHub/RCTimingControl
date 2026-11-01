package dev.monkeypatch.rctiming.screens;

import dev.monkeypatch.rctiming.infrastructure.network.LanAddresses;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which pages may be put on the screens (#708). */
class ScreenPagePolicyTest {

    private final LanAddresses lan = mock(LanAddresses.class);

    private ScreenPagePolicy policy(boolean restrict, String... allowed) {
        when(lan.urls()).thenReturn(List.of("http://192.168.1.5:8080/", "http://timing.local:8080/"));
        return new ScreenPagePolicy(new ScreensProperties(false, restrict, List.of(allowed), false), lan);
    }

    @Test
    void pagesOnThisServersAddressesAreAllowedWhateverThePort() {
        ScreenPagePolicy policy = policy(true);

        assertThatCode(() -> policy.check("http://192.168.1.5:8080/boards/results", "Page")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("http://TIMING.local/boards/top", "Page")).doesNotThrowAnyException();
    }

    @Test
    void aPageElsewhereIsRefusedUnlessItsHostIsAllowed() {
        assertThatThrownBy(() -> policy(true).check("http://evil.example/x", "Page 2"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Page 2")
                .hasMessageContaining("allowed-hosts");
        assertThatThrownBy(() -> policy(true).check("http://localhost:8080/boards/top", "Page"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatCode(() -> policy(true, "club.example").check("https://club.example/live", "Page")).doesNotThrowAnyException();
    }

    @Test
    void allowedHostsMayBeWrittenWithAPortAsAUrlOrAsABareIpv6Address() {
        ScreenPagePolicy policy = policy(true, "club.example:8080", "https://other.example/", "::1");

        assertThatCode(() -> policy.check("http://club.example/x", "Page")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("http://other.example/x", "Page")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("http://[::1]:8080/x", "Page")).doesNotThrowAnyException();
    }

    @Test
    void aUserInfoTrickDoesNotGetPastTheHostCheck() {
        assertThatThrownBy(() -> policy(true).check("http://192.168.1.5@evil.example/x", "Page"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRestrictionCanBeSwitchedOff() {
        assertThatCode(() -> policy(false).check("http://anything.example/", "Page")).doesNotThrowAnyException();
    }
}
