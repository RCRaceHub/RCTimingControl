package dev.monkeypatch.rctiming.service;

import dev.monkeypatch.rctiming.domain.club.ClubProfileService;
import dev.monkeypatch.rctiming.domain.club.ClubProfileService.ClubDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvisioningServiceTest {

    @Mock
    private SetupService setupService;
    @Mock
    private ClubProfileService clubProfileService;
    private ProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new ProvisioningService(setupService, clubProfileService);
    }

    private static ProvisioningProperties settings(String password, String clubName, String decoderHost) {
        return new ProvisioningProperties(
                new ProvisioningProperties.Admin("admin@club.test", password, "Ann", "Admin"),
                new ProvisioningProperties.Club(clubName, " ", null, null, "Europe/London"),
                new ProvisioningProperties.Decoder(decoderHost, 5100, "RC4"));
    }

    @Test
    void applyCreatesAdminClubAndDecoderOnAFreshDatabase() {
        when(setupService.getStatus()).thenReturn(new SetupService.Status(false, false));

        assertThat(service.apply(settings("longenough", "Wyvern RC", "192.168.1.20"))).isTrue();

        verify(setupService).bootstrap("admin@club.test", "longenough", "Ann", "Admin");
        ArgumentCaptor<ClubDetails> club = ArgumentCaptor.forClass(ClubDetails.class);
        verify(clubProfileService).createOrUpdateProfile(any(), club.capture());
        assertThat(club.getValue().name()).isEqualTo("Wyvern RC");
        assertThat(club.getValue().email()).isNull();
        assertThat(club.getValue().timezone()).isEqualTo("Europe/London");
        verify(clubProfileService).updateDecoderConfig(any(), eq("192.168.1.20"), eq(5100), eq("RC4"));
    }

    @Test
    void applyChangesNothingOnceAnAccountExists() {
        when(setupService.getStatus()).thenReturn(new SetupService.Status(true, true));

        assertThat(service.apply(settings("longenough", "Wyvern RC", "192.168.1.20"))).isFalse();

        verify(setupService, never()).bootstrap(any(), any(), any(), any());
        verifyNoInteractions(clubProfileService);
    }

    @Test
    void applyLeavesTheClubStepToTheWizardWhenNoClubNameIsGiven() {
        when(setupService.getStatus()).thenReturn(new SetupService.Status(false, false));

        service.apply(settings("longenough", null, null));

        verify(setupService).bootstrap("admin@club.test", "longenough", "Ann", "Admin");
        verifyNoInteractions(clubProfileService);
    }

    @Test
    void applyRefusesAnAdminWithoutAPasswordNamingTheSetting() {
        when(setupService.getStatus()).thenReturn(new SetupService.Status(false, false));

        assertThatThrownBy(() -> service.apply(settings(" ", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rctiming.provisioning.admin.password");
        verify(setupService, never()).bootstrap(any(), any(), any(), any());
    }

    @Test
    void applyRefusesADecoderWithNoClubBecauseTheAddressLivesInTheClubProfile() {
        when(setupService.getStatus()).thenReturn(new SetupService.Status(false, false));

        assertThatThrownBy(() -> service.apply(settings("longenough", null, "192.168.1.20")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("club.name");
    }

    @Test
    void enabledOnlyWhenAnAdminEmailIsSet() {
        assertThat(settings("x", null, null).enabled()).isTrue();
        assertThat(new ProvisioningProperties(
                new ProvisioningProperties.Admin(null, null, null, null),
                new ProvisioningProperties.Club(null, null, null, null, "UTC"),
                new ProvisioningProperties.Decoder(null, 5100, "RC4")).enabled()).isFalse();
    }
}
