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
    void beforeAProfileExists_theBattleGapIsTheDefault() {
        when(repository.findCurrent()).thenReturn(Optional.empty());

        assertThat(service.battleGapMs()).isEqualTo(ClubProfile.DEFAULT_BATTLE_GAP_MS);
    }

    @Test
    void aSavedBattleGap_isTheClubs() {
        ClubProfile profile = new ClubProfile();
        profile.setBattleGapMs(1500);
        when(repository.findCurrent()).thenReturn(Optional.of(profile));

        assertThat(service.battleGapMs()).isEqualTo(1500);
    }

    @Test
    void aBattleGapOutsideTheRange_isRefusedAndSavesNothing() {
        assertThatThrownBy(() -> service.changeBattleGap(null, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeBattleGap(null, ClubProfile.MAX_BATTLE_GAP_MS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    private static ClubProfile profileWithVoice(String voice) {
        ClubProfile profile = new ClubProfile();
        profile.setDefaultVoiceId(voice);
        return profile;
    }
}
