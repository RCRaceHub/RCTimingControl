package dev.monkeypatch.rctiming.domain.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The rules for which addresses an entry import's pictures may come from, and what counts as a picture (#699). */
class PhotoLinkAndFetcherTest {

    @Test
    void anHttpsAddressWithAHostIsUsable_andNothingNamedIsFine() {
        assertThat(PhotoLink.problem("https://cdn.example.org/a.png")).isNull();
        assertThat(PhotoLink.problem(null)).isNull();
        assertThat(PhotoLink.problem("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://cdn.example.org/a.png", "ftp://cdn.example.org/a.png", "file:///etc/passwd",
            "https://user:secret@cdn.example.org/a.png", "https:///a.png", "not an address", "/relative/a.png"})
    void anythingElseIsRefused(String url) {
        assertThat(PhotoLink.problem(url)).isNotNull();
    }

    @Test
    void anAddressOverTheLimitIsRefused() {
        assertThat(PhotoLink.problem("https://cdn.example.org/" + "a".repeat(PhotoLink.URL_MAX))).contains("longer than");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "192.168.1.20", "172.16.0.9", "169.254.169.254", "0.0.0.0",
            "224.0.0.1", "::1", "fe80::1", "fd12:3456::1", "fc00::1"})
    void privateLoopbackAndLinkLocalAddressesAreNotFetched(String address) throws UnknownHostException {
        assertThat(PhotoFetcher.isPublic(InetAddress.getByName(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "2606:4700:4700::1111"})
    void publicAddressesAreFetched(String address) throws UnknownHostException {
        assertThat(PhotoFetcher.isPublic(InetAddress.getByName(address))).isTrue();
    }

    @Test
    void theTypeComesFromTheFirstBytes_soSvgAndHtmlAreRefusedWhateverTheServerSays() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

        assertThat(DriverMediaService.sniffType(png)).isEqualTo("image/png");
        assertThat(DriverMediaService.sniffType(jpeg)).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> DriverMediaService.sniffType("<svg onload=alert(1)>".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DriverMediaService.sniffType("<html></html>".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
