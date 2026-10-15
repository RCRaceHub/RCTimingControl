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

    @Test
    void settingsSavedBeforeTheFinishCallsCallEachCarAndUseTheStandardTimeWording() throws Exception {
        ClubAudioSettings settings = new ObjectMapper()
                .readValue("{\"announceFinish\": true}", ClubAudioSettings.class);

        assertThat(settings.announceCarFinish()).isTrue();
        assertThat(settings.timeUpText()).isEqualTo(ClubAudioSettings.DEFAULT_TIME_UP_TEXT);
    }

    @Test
    void theClubsOwnTimeWordingIsKeptAndABlankOneGoesBackToTheStandard() {
        ClubAudioSettings own = new ClubAudioSettings(true, true, true, true, true, 3, null,
                null, null, null, null, null, null, false, "  Time! Finish your lap.  ");
        ClubAudioSettings blank = new ClubAudioSettings(true, true, true, true, true, 3, null,
                null, null, null, null, null, null, true, "   ");

        assertThat(own.timeUpText()).isEqualTo("Time! Finish your lap.");
        assertThat(own.announceCarFinish()).isFalse();
        assertThat(blank.timeUpText()).isEqualTo(ClubAudioSettings.DEFAULT_TIME_UP_TEXT);
    }

    @Test
    void settingsSavedBeforeTheWordingAndTuningTakeTheStandardOnes() throws Exception {
        ClubAudioSettings settings = new ObjectMapper()
                .readValue("{\"announceFinish\": true}", ClubAudioSettings.class);

        assertThat(settings.wording()).isEqualTo(ClubAudioSettings.Wording.STANDARD);
        assertThat(settings.wording().go()).isEqualTo("Race started.");
        assertThat(settings.wording().carFinish()).isEqualTo("Car {car}, {name}.");
        assertThat(settings.tuning().runningOrderEarlyPeriodMinutes()).isEqualTo(10);
        assertThat(settings.tuning().runningOrderEarlyEveryMinutes()).isEqualTo(2);
        assertThat(settings.tuning().runningOrderLaterEveryMinutes()).isEqualTo(5);
        assertThat(settings.tuning().finalSeconds()).isEqualTo(5);
        assertThat(settings.tuning().lapBeepHz()).isEqualTo(440);
        assertThat(settings.tuning().lapBeepMs()).isEqualTo(200);
        assertThat(settings.tickSound()).isTrue();
        assertThat(settings.finalBeeps()).isTrue();
    }

    @Test
    void aBlankLineOfWordingGoesBackToTheStandardAndTheClubsOwnIsKept() {
        ClubAudioSettings.Wording wording = new ClubAudioSettings.Wording(
                "  Get ready, {seconds}.  ", "   ", null, "Red flag.", null, null, null, null, null, null, null);

        assertThat(wording.startLeadIn()).isEqualTo("Get ready, {seconds}.");
        assertThat(wording.gridCall()).isEqualTo(ClubAudioSettings.Wording.DEFAULT_GRID_CALL);
        assertThat(wording.stop()).isEqualTo("Red flag.");
        assertThat(wording.resume()).isEqualTo(ClubAudioSettings.Wording.DEFAULT_RESUME);
    }

    @Test
    void wordingFillsInTheValuesItIsGiven() {
        assertThat(ClubAudioSettings.Wording.fill("Car {car}, {name}!", "car", "12", "name", "Alan Smith"))
                .isEqualTo("Car 12, Alan Smith!");
    }

    @Test
    void theSoundsFromBeforeTheTickAndLapBeepHaveNoneOfThem() throws Exception {
        ClubAudioSettings settings = new ObjectMapper().readValue(
                "{\"hornSounds\": {\"startUrl\": \"a.wav\", \"startMs\": 900}}", ClubAudioSettings.class);

        assertThat(settings.hornSounds().startMs()).isEqualTo(900);
        assertThat(settings.hornSounds().tickUrl()).isNull();
        assertThat(settings.hornSounds().lapBeepUrl()).isNull();
        assertThat(settings.hornSounds().withTick("t.wav", 300).withLapBeep("l.wav", 200).startUrl())
                .isEqualTo("a.wav");
    }

    @Test
    void theClubsWordingAndTuningSurviveBeingSavedAndRead() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClubAudioSettings own = ClubAudioSettings.defaults()
                .withHornSounds(ClubAudioSettings.HornSounds.NONE.withTick("t.wav", 250));
        ClubAudioSettings changed = new ClubAudioSettings(true, true, true, true, true, 3, null, null, null, null,
                null, null, own.hornSounds(), null, null, false, false,
                new ClubAudioSettings.Wording(null, null, "Go go go!", null, null, null, null, null, null, null,
                        null),
                new ClubAudioSettings.Tuning(5, 1, 3, 3, 600, 300));

        ClubAudioSettings read = mapper.readValue(mapper.writeValueAsString(changed), ClubAudioSettings.class);

        assertThat(read.wording().go()).isEqualTo("Go go go!");
        assertThat(read.tuning().finalSeconds()).isEqualTo(3);
        assertThat(read.tuning().lapBeepHz()).isEqualTo(600);
        assertThat(read.tickSound()).isFalse();
        assertThat(read.finalBeeps()).isFalse();
        assertThat(read.hornSounds().tickMs()).isEqualTo(250);
    }
}
