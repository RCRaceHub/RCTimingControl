package dev.monkeypatch.rctiming.domain.club;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClubAudioSettingsTest {

    @Test
    void settingsSavedBeforeTheHornsHaveThemOnAndNoLeadIn() throws Exception {
        String saved = "{\"announceFinish\": true, \"announceLapBeep\": true, \"announceStagger\": true,"
                + " \"announceCountdown\": true, \"runningOrderDepth\": 3, \"announceRunningOrder\": true}";

        ClubAudioSettings settings = new ObjectMapper().readValue(saved, ClubAudioSettings.class);

        assertThat(settings.startHorn()).isTrue();
        assertThat(settings.finishHorn()).isTrue();
        assertThat(settings.announceRaceCalls()).isTrue();
        assertThat(settings.announceNextRace()).isTrue();
        assertThat(settings.startCountdownSeconds()).isEqualTo(10);
    }

    @Test
    void savedChoicesAreKept() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClubAudioSettings changed = new ClubAudioSettings(true, true, true, true, true, 3, null,
                false, false, false, false, 10);

        ClubAudioSettings read = mapper.readValue(mapper.writeValueAsString(changed), ClubAudioSettings.class);

        assertThat(read.startHorn()).isFalse();
        assertThat(read.finishHorn()).isFalse();
        assertThat(read.announceRaceCalls()).isFalse();
        assertThat(read.announceNextRace()).isFalse();
        assertThat(read.startCountdownSeconds()).isEqualTo(10);
    }
}
