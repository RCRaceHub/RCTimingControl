package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClubProfileServiceTest {

    private final ClubProfileRepository repository = mock(ClubProfileRepository.class);
    private final ClubProfileService service = new ClubProfileService(repository,
            mock(GoverningBodyAffiliationRepository.class), mock(ApplicationEventPublisher.class),
            mock(AuditService.class));

    @Test
    void beforeAProfileExists_readingTheAudioSettingsGivesTheDefaultsAndCreatesNothing() {
        when(repository.findCurrent()).thenReturn(Optional.empty());

        assertThat(service.audioSettings()).usingRecursiveComparison().isEqualTo(ClubAudioSettings.defaults());
        assertThat(service.defaultVoiceId()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void aBlankVoice_meansThePiperDefault() {
        when(repository.findCurrent()).thenReturn(Optional.of(profileWithVoice(" ")));

        assertThat(service.defaultVoiceId()).isEmpty();
    }

    @Test
    void aChosenVoice_isTheClubsVoice() {
        when(repository.findCurrent()).thenReturn(Optional.of(profileWithVoice("en_GB-jenny_dioco-medium")));

        assertThat(service.defaultVoiceId()).contains("en_GB-jenny_dioco-medium");
    }

    @Test
    void beforeAProfileExists_theLiveTimingSettingsAreTheDefaults() {
        when(repository.findCurrent()).thenReturn(Optional.empty());

        assertThat(service.liveTimingSettings()).isEqualTo(LiveTimingSettings.defaults());
    }

    @Test
    void savedLiveTimingSettings_areTheClubs() {
        ClubProfile profile = new ClubProfile();
        LiveTimingSettings own = new LiveTimingSettings(1500, 100, 250, 3, 800, 500, java.util.List.of("pace"));
        profile.setLiveTimingSettings(own);
        when(repository.findCurrent()).thenReturn(Optional.of(profile));

        assertThat(service.liveTimingSettings()).isEqualTo(own);
    }

    @Test
    void liveTimingSettingsOutOfRange_areRefusedAndSaveNothing() {
        LiveTimingSettings defaults = LiveTimingSettings.defaults();
        assertThatThrownBy(() -> service.changeLiveTimingSettings(null,
                new LiveTimingSettings(-1, 150, 300, 2, 1000, 1000, java.util.List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeLiveTimingSettings(null,
                new LiveTimingSettings(defaults.battleGapMs(), 400, 300, 2, 1000, 1000, java.util.List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gaining-fast");
        assertThatThrownBy(() -> service.changeLiveTimingSettings(null,
                new LiveTimingSettings(2000, 150, 300, 11, 1000, 1000, java.util.List.of()))).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownRowIcon_isRefused_andNullsAndRepeatsAreDropped() {
        assertThatThrownBy(() -> service.changeLiveTimingSettings(null,
                new LiveTimingSettings(2000, 150, 300, 2, 1000, 1000, java.util.List.of("sparkles"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sparkles");
        assertThat(new LiveTimingSettings(2000, 150, 300, 2, 1000, 1000, java.util.Arrays.asList("pace", null, "pace"))
                .hiddenRowIcons()).containsExactly("pace");
        verify(repository, never()).save(any());
    }

    private static ClubProfile profileWithVoice(String voice) {
        ClubProfile profile = new ClubProfile();
        profile.setDefaultVoiceId(voice);
        return profile;
    }
}
