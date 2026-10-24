package dev.monkeypatch.rctiming.infrastructure.network;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MdnsAdvertiserTest {

    @Test
    void aNameBecomesALowerCaseHostLabelWithoutTheDotLocal() {
        assertThat(MdnsAdvertiser.hostLabel("timing")).contains("timing");
        assertThat(MdnsAdvertiser.hostLabel(" Timing.LOCAL ")).contains("timing");
        assertThat(MdnsAdvertiser.hostLabel("club-timing-2")).contains("club-timing-2");
    }

    @Test
    void blankOrUnusableNamesAreNotAnnounced() {
        assertThat(MdnsAdvertiser.hostLabel(null)).isEqualTo(Optional.empty());
        assertThat(MdnsAdvertiser.hostLabel("")).isEqualTo(Optional.empty());
        assertThat(MdnsAdvertiser.hostLabel("my timing")).isEqualTo(Optional.empty());
        assertThat(MdnsAdvertiser.hostLabel("-timing")).isEqualTo(Optional.empty());
        assertThat(MdnsAdvertiser.hostLabel("a.b")).isEqualTo(Optional.empty());
    }
}
